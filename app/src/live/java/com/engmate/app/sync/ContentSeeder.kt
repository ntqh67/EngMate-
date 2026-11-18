package com.engmate.app.sync

import android.content.Context
import android.util.Log
import com.engmate.app.core.database.AnswerType
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.quiz.data.local.QuestionDao
import com.engmate.app.feature.quiz.data.local.QuestionEntity
import com.engmate.app.feature.quiz.data.local.QuestionOptionEntity
import com.engmate.app.feature.quiz.data.local.QuestionWithOptions
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Extra grammar lessons (tenses and verbs) shipped in assets/grammar_seed.json, with fixed ids (lessons 101+, questions 11000+).
 *
 * - [seedRoom] adds them to Room on every device, signed in or not (skips ones already present).
 * - [uploadMissing] runs once signed in and writes only the lessons that Firestore does not have yet, so the content sync
 *   (which mirrors Firestore into Room and removes local lessons missing there) keeps them, and console edits are never overwritten.
 */
@Singleton
class ContentSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lessons: GrammarLessonDao,
    private val questions: QuestionDao,
    private val remote: FirestoreContent,
) {
    private data class SeedFile(val lessons: List<SeedLesson> = emptyList())
    private data class SeedExample(val en: String = "", val vi: String = "")
    private data class SeedQuestion(val type: String = "MC", val q: String = "", val opts: List<String> = emptyList(), val correct: Int = 0,
                                    val accept: List<String> = emptyList(), val exp: String = "")
    private data class SeedLesson(val id: Long = 0, val title: String = "", val level: String = "", val summary: String = "", val content: String = "",
                                  val examples: List<SeedExample> = emptyList(), val questions: List<SeedQuestion> = emptyList())

    private val mutex = Mutex()

    private fun load(): List<Pair<GrammarLessonEntity, List<QuestionWithOptions>>> {
        val now = System.currentTimeMillis()
        val file = context.assets.open("grammar_seed.json").bufferedReader(Charsets.UTF_8).use { Gson().fromJson(it, SeedFile::class.java) }
        return file.lessons.mapIndexed { index, l ->
            val lesson = GrammarLessonEntity(l.id, l.title, l.level, l.summary.ifBlank { null }, l.content,
                l.examples.map { GrammarExample(it.en, it.vi) }, orderIndex = 10 + index, updatedAt = now)
            val qs = l.questions.mapIndexed { i, q ->
                val id = QUESTION_BASE + l.id * 10 + i
                val multiple = q.type == "MC"
                val question = QuestionEntity(id, null, l.id, false, if (multiple) AnswerType.SINGLE_CHOICE else AnswerType.TEXT, q.q, q.exp.ifBlank { null }, createdAt = now)
                val options = if (multiple) q.opts.mapIndexed { n, o -> QuestionOptionEntity(questionId = id, content = o, isCorrect = n == q.correct, orderIndex = n) }
                else q.accept.mapIndexed { n, o -> QuestionOptionEntity(questionId = id, content = o, isCorrect = true, orderIndex = n) }
                QuestionWithOptions(question, options)
            }
            lesson to qs
        }
    }

    suspend fun seedRoom() = mutex.withLock {
        for ((lesson, qs) in load()) {
            if (lessons.get(lesson.id) != null) continue
            lessons.upsert(lesson)
            qs.forEach { questions.upsertWithOptions(it.question, it.options) }
        }
    }

    suspend fun uploadMissing() = mutex.withLock {
        val ref = remote.lessons() ?: return@withLock
        uploadLegacy()
        for ((lesson, qs) in load()) {
            try {
                val exists = withTimeoutOrNull(10_000) { ref.document(lesson.id.toString()).get().await().exists() } ?: continue
                if (exists) continue
                remote.putLesson(lesson)
                qs.forEach { remote.putQuestion(it) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(FirestoreContent.TAG, "Seed upload ${lesson.id} failed", e) }
        }
    }

    /**
     * The original lessons and questions (created with the database, ids below [QUESTION_BASE]) may be missing in Firestore if the first
     * upload was rejected. Content sync would then delete them from Room, so push the missing ones once per install.
     */
    private suspend fun uploadLegacy() {
        val prefs = context.getSharedPreferences("content_seed", Context.MODE_PRIVATE)
        if (prefs.getBoolean(LEGACY_FLAG, false)) return
        try {
            val lessonIds = withTimeoutOrNull(10_000) { remote.lessons()?.get()?.await()?.documents?.map { it.id }?.toSet() } ?: return
            val questionIds = withTimeoutOrNull(10_000) { remote.questions()?.get()?.await()?.documents?.map { it.id }?.toSet() } ?: return
            lessons.all().filter { it.id < LEGACY_LESSON_LIMIT && it.id.toString() !in lessonIds }.forEach { remote.putLesson(it) }
            questions.all().filter { it.question.id < QUESTION_BASE && it.question.id.toString() !in questionIds }.forEach { remote.putQuestion(it) }
            prefs.edit().putBoolean(LEGACY_FLAG, true).apply()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(FirestoreContent.TAG, "Legacy upload failed", e) }
    }

    companion object {
        /** Lessons and questions shipped with the app; content sync never removes them locally, even if the upload to Firestore failed. */
        fun isBundledLesson(id: Long) = id in 101L..999L
        fun isBundledQuestion(id: Long) = id >= QUESTION_BASE
        private const val QUESTION_BASE = 10_000L
        private const val LEGACY_LESSON_LIMIT = 100L
        private const val LEGACY_FLAG = "legacy_uploaded"
    }
}
