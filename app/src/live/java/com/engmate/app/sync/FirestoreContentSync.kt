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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Firestore is the source of truth for grammar lessons and questions; Room is the offline copy the UI reads.
 *
 * While a user is signed in, only published lesson metadata is watched. Full lesson content and
 * questions are fetched on demand and cached in Room. Missing documents never erase offline content.
 * Questions that are part of someone's quiz history on this device are never changed or deleted,
 * matching the rules the admin screens already enforce.
 *
 * Shared content is published by administrators; learner devices never upload seeds.
 */
@Singleton
class FirestoreContentSync @Inject constructor(
    private val remote: FirestoreContent,
    private val session: UserSession,
    private val database: EngMateDatabase,
    private val lessons: GrammarLessonDao,
    private val questions: QuestionDao,
    private val userData: UserDataSync,
    private val seeder: ContentSeeder,
) : ContentSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val lessonLocks = ConcurrentHashMap<Long, Mutex>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start() {
        userData.start()
        scope.launch { runCatching { seeder.seedRoom() }.onFailure { Log.w(FirestoreContent.TAG, "Seeding lessons failed", it) } }
        val lessonsRef = remote.catalog()?.whereEqualTo("published", true) ?: return
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // Security rules only allow signed-in users, so listen per session and stop on sign-out.
            session.user.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else kotlinx.coroutines.flow.flow {
                    // Seed only Room. Publishing shared content requires an administrator.
                    seeder.seedRoom()
                    emitAll(lessonsRef.snapshotFlow().catch { FirestoreContent.log("lesson catalog", it) })
                }
            }.collect { lessonSnap ->
                runCatching { applyCatalog(lessonSnap) }.onFailure { Log.w(FirestoreContent.TAG, "Catalog sync failed", it) }
            }
        }
    }

    private suspend fun applyCatalog(snapshot: QuerySnapshot) {
        if (snapshot.metadata.isFromCache && snapshot.isEmpty) return
        val catalog = snapshot.documents.mapNotNull { it.toLessonCatalogEntity() }
        database.withTransaction {
            val localLessons = lessons.all().associateBy { it.id }
            catalog.forEach { remoteLesson ->
                val local = localLessons[remoteLesson.id]
                val row = if (local == null) remoteLesson else remoteLesson.copy(content = local.content, examples = local.examples)
                if (local != row) lessons.upsert(row)
            }
        }
    }

    /** Fetch a full lesson and only that lesson's quiz. Existing cached rows remain usable offline. */
    override suspend fun loadLesson(lessonId: Long) = lessonLocks.getOrPut(lessonId) { Mutex() }.withLock {
        val db = remote.firestore ?: return@withLock
        val catalog = db.collection(FirestoreContent.CATALOG).document(lessonId.toString()).get().await()
        if (!catalog.exists() || catalog.getBoolean("published") != true) return@withLock
        val version = catalog.getLong("contentVersion") ?: 1L
        val local = lessons.get(lessonId)
        val prefs = remote.context.getSharedPreferences("grammar_content_versions", android.content.Context.MODE_PRIVATE)
        if (local != null && local.content.isNotBlank() && prefs.getLong(lessonId.toString(), 0L) == version) return@withLock
        val fullDocument = db.collection(FirestoreContent.LESSONS).document(lessonId.toString()).get().await()
        if (!fullDocument.exists() || fullDocument.getBoolean("published") != true) return@withLock
        val lesson = fullDocument.toLessonEntity() ?: return@withLock
        val questionSnapshots = db.collection(FirestoreContent.QUESTIONS)
            .whereEqualTo("published", true).whereEqualTo("lessonId", lessonId).get().await()
        val validQuestions = questionSnapshots.documents.mapNotNull { it.toQuestion() }
        database.withTransaction {
            lessons.upsert(lesson)
            val localQuestions = questions.forLesson(lessonId).associateBy { it.question.id }
            validQuestions.forEach { (question, options) ->
                val old = localQuestions[question.id]
                if (old != null && (old.sameAs(question, options) || questions.isAnswered(question.id))) return@forEach
                try { questions.upsertWithOptions(question, options) } catch (e: SQLiteConstraintException) {
                    Log.w(FirestoreContent.TAG, "Skipped question ${question.id}", e)
                }
            }
        }
        prefs.edit().putLong(lessonId.toString(), version).apply()
    }

    private fun QuestionWithOptions.sameAs(remote: QuestionEntity, remoteOptions: List<QuestionOptionEntity>): Boolean {
        val mine = options.sortedWith(compareBy({ it.orderIndex }, { it.id })).map { Triple(it.content, it.isCorrect, it.orderIndex) }
        val theirs = remoteOptions.map { Triple(it.content, it.isCorrect, it.orderIndex) }
        return question.copy(createdAt = remote.createdAt) == remote && mine == theirs
    }
}
