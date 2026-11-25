package com.engmate.app.sync

import android.content.Context
import androidx.room.withTransaction
import com.engmate.app.core.database.AnswerType
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.grammar.domain.LessonInput
import com.engmate.app.feature.grammar.domain.LessonValidator
import com.engmate.app.feature.quiz.data.local.QuestionDao
import com.engmate.app.feature.quiz.data.local.QuestionEntity
import com.engmate.app.feature.quiz.data.local.QuestionOptionEntity
import com.engmate.app.feature.quiz.data.local.QuestionWithOptions
import com.engmate.app.feature.quiz.data.local.toDomain
import com.engmate.app.feature.quiz.domain.OptionInput
import com.engmate.app.feature.quiz.domain.QuestionInput
import com.engmate.app.feature.quiz.domain.QuestionValidator
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The 17 bundled baseline grammar lessons stay in assets/grammar_seed.json / GrammarQuizSeed.
 *
 * - [seedRoom] adds them to Room on every device, signed in or not (skips ones already present).
 * - Shared Firestore content is published separately by administrators, never by learner devices.
 */
@Singleton
class ContentSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lessons: GrammarLessonDao,
    private val questions: QuestionDao,
    private val database: EngMateDatabase,
) {
    private data class SeedFile(val lessons: List<SeedLesson> = emptyList())
    private data class SeedExample(val en: String = "", val vi: String = "")
    private data class SeedQuestion(val id: Long = 0, val type: String = "MC", val q: String = "", val opts: List<String> = emptyList(), val correct: Int = 0,
                                    val accept: List<String> = emptyList(), val exp: String = "")
    private data class SeedLesson(val id: Long = 0, val title: String = "", val level: String = "", val summary: String = "", val content: String = "",
                                  val examples: List<SeedExample> = emptyList(), val questions: List<SeedQuestion> = emptyList(),
                                  val orderIndex: Int? = null)

    private val mutex = Mutex()

    private fun load(asset: String): List<Pair<GrammarLessonEntity, List<QuestionWithOptions>>> {
        val now = System.currentTimeMillis()
        val file = context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { Gson().fromJson(it, SeedFile::class.java) }
        return file.lessons.mapIndexed { index, l ->
            require(LessonValidator.errors(LessonInput(l.title, l.level, l.summary, l.content,
                l.examples.map { GrammarExample(it.en, it.vi) })).isEmpty()) { "Invalid lesson in $asset: ${l.id}" }
            val lesson = GrammarLessonEntity(l.id, l.title, l.level, l.summary.ifBlank { null }, l.content,
                l.examples.map { GrammarExample(it.en, it.vi) }, orderIndex = l.orderIndex ?: (10 + index), updatedAt = now)
            val qs = l.questions.mapIndexed { i, q ->
                val id = q.id.takeIf { it != 0L } ?: (QUESTION_BASE + l.id * 10 + i)
                require(q.type == "MC" || q.type == "FB") { "Unsupported seed question type: ${q.type}" }
                val multiple = q.type == "MC"
                val question = QuestionEntity(id, null, l.id, false, if (multiple) AnswerType.SINGLE_CHOICE else AnswerType.TEXT, q.q, q.exp.ifBlank { null }, createdAt = now)
                val options = if (multiple) q.opts.mapIndexed { n, o -> QuestionOptionEntity(questionId = id, content = o, isCorrect = n == q.correct, orderIndex = n) }
                else q.accept.mapIndexed { n, o -> QuestionOptionEntity(questionId = id, content = o, isCorrect = true, orderIndex = n) }
                val result = QuestionWithOptions(question, options)
                require(QuestionValidator.errors(QuestionInput(lessonId = l.id, answerType = result.toDomain().answerType,
                    content = q.q, explanation = q.exp, options = options.map { OptionInput(it.content, it.isCorrect) })).isEmpty()) {
                    "Invalid seed question: $id"
                }
                result
            }
            lesson to qs
        }
    }

    suspend fun seedRoom() = mutex.withLock {
        val content = load("grammar_seed.json")
        require(content.map { it.first.id }.distinct().size == content.size) { "Duplicate seed lesson ids" }
        val questionIds = content.flatMap { it.second }.map { it.question.id }
        require(questionIds.distinct().size == questionIds.size) { "Duplicate seed question ids" }
        database.withTransaction {
            for ((lesson, qs) in content) {
                // Immutable seed ids: never overwrite edits, option ids, or answered questions.
                if (lessons.get(lesson.id) == null) lessons.upsert(lesson)
                qs.forEach {
                    if (questions.get(it.question.id) == null) questions.upsertWithOptions(it.question, it.options)
                }
            }
        }
    }

    companion object {
        private const val QUESTION_BASE = 10_000L
    }
}
