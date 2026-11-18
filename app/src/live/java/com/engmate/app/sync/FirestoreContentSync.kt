package com.engmate.app.sync

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.withTransaction
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.core.sync.ContentSync
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.quiz.data.local.QuestionDao
import com.engmate.app.feature.quiz.data.local.QuestionEntity
import com.engmate.app.feature.quiz.data.local.QuestionOptionEntity
import com.engmate.app.feature.quiz.data.local.QuestionWithOptions
import com.google.firebase.firestore.QuerySnapshot
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Firestore is the source of truth for grammar lessons and questions; Room is the offline copy the UI reads.
 *
 * While a user is signed in, every change in either collection is mirrored into Room:
 * new and changed documents are upserted, documents removed from Firestore are deleted locally.
 * Rows that are part of someone's quiz history on this device are never changed or deleted,
 * matching the rules the admin screens already enforce.
 *
 * On the very first run against an empty Firestore project, the local seed content is uploaded
 * so the Firebase console starts with the sample lessons.
 */
@Singleton
class FirestoreContentSync @Inject constructor(
    private val remote: FirestoreContent,
    private val session: UserSession,
    private val database: EngMateDatabase,
    private val lessons: GrammarLessonDao,
    private val questions: QuestionDao,
) : ContentSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start() {
        val lessonsRef = remote.lessons() ?: return
        val questionsRef = remote.questions() ?: return
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // Security rules only allow signed-in users, so listen per session and stop on sign-out.
            session.user.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else combine(lessonsRef.snapshotFlow(), questionsRef.snapshotFlow(), ::Pair)
                    .catch { FirestoreContent.log("listen", it) }
            }.collect { (lessonSnap, questionSnap) ->
                runCatching { apply(lessonSnap, questionSnap) }.onFailure { Log.w(FirestoreContent.TAG, "Sync failed", it) }
            }
        }
    }

    private suspend fun apply(lessonSnap: QuerySnapshot, questionSnap: QuerySnapshot) {
        // An empty cached snapshot (first launch while offline) says nothing about the server, so don't wipe Room with it.
        if ((lessonSnap.metadata.isFromCache && lessonSnap.isEmpty) || (questionSnap.metadata.isFromCache && questionSnap.isEmpty)) return
        if (lessonSnap.isEmpty && questionSnap.isEmpty) {
            remote.uploadAll(lessons.all(), questions.all())
            return
        }
        // Ids present remotely, including documents that failed validation: those keep their local copy.
        val remoteLessonIds = lessonSnap.documents.mapNotNull { it.id.toLongOrNull() }.toSet()
        val remoteQuestionIds = questionSnap.documents.mapNotNull { it.id.toLongOrNull() }.toSet()
        val validLessons = lessonSnap.documents.mapNotNull { it.toLessonEntity() }
        val validQuestions = questionSnap.documents.mapNotNull { it.toQuestion() }

        database.withTransaction {
            // 1. Lessons first, so questions can point at them.
            val localLessons = lessons.all().associateBy { it.id }
            validLessons.filter { localLessons[it.id] != it }.forEach { lessons.upsert(it) }

            // 2. Questions removed from Firestore.
            val localQuestions = questions.all().associateBy { it.question.id }
            localQuestions.keys.filter { it !in remoteQuestionIds && !questions.isAnswered(it) }.forEach { questions.delete(it) }

            // 3. New or changed questions. Unchanged ones are skipped so option ids stay stable during a quiz.
            validQuestions.forEach { (question, options) ->
                val local = localQuestions[question.id]
                if (local != null && (local.sameAs(question, options) || questions.isAnswered(question.id))) return@forEach
                if (question.lessonId != null && lessons.get(question.lessonId) == null) return@forEach
                try { questions.upsertWithOptions(question, options) } catch (e: SQLiteConstraintException) {
                    Log.w(FirestoreContent.TAG, "Skipped question ${question.id}", e)
                }
            }

            // 4. Lessons removed from Firestore, unless someone on this device already practised them.
            localLessons.keys.filter { it !in remoteLessonIds && lessons.usageCount(it) == 0 }.forEach { lessons.deleteWithQuestions(it) }
        }
    }

    private fun QuestionWithOptions.sameAs(remote: QuestionEntity, remoteOptions: List<QuestionOptionEntity>): Boolean {
        val mine = options.sortedWith(compareBy({ it.orderIndex }, { it.id })).map { Triple(it.content, it.isCorrect, it.orderIndex) }
        val theirs = remoteOptions.map { Triple(it.content, it.isCorrect, it.orderIndex) }
        return question.copy(createdAt = remote.createdAt) == remote && mine == theirs
    }
}
