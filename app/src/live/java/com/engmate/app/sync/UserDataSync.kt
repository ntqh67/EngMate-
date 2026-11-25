package com.engmate.app.sync

import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.engmate.app.core.database.ActivityType
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.core.sync.HandbookGuard
import com.engmate.app.core.sync.RefreshResult
import com.engmate.app.core.sync.SyncRefresher
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.flashcard.data.local.FlashcardCollectionEntity
import com.engmate.app.feature.flashcard.data.local.FlashcardDao
import com.engmate.app.feature.flashcard.data.local.FlashcardEntity
import com.engmate.app.feature.flashcard.data.local.WordProgressEntity
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.notebook.data.local.NotebookDao
import com.engmate.app.feature.notebook.data.local.NotebookWordEntity
import com.engmate.app.feature.notebook.data.local.WordNoteEntity
import com.engmate.app.feature.profile.data.local.UserProfileDao
import com.engmate.app.feature.progress.data.local.LearningActivityEntity
import com.engmate.app.feature.progress.data.local.StudyDao
import com.engmate.app.feature.quiz.data.local.AttemptAnswerEntity
import com.engmate.app.feature.quiz.data.local.QuestionDao
import com.engmate.app.feature.quiz.data.local.QuizAttemptDao
import com.engmate.app.feature.quiz.data.local.QuizAttemptEntity
import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private typealias Docs = Map<String, Map<String, Any?>>

/**
 * Keeps all of a signed-in learner's data in Cloud Firestore under users/{uid}/..., next to the Room copy the UI reads.
 *
 *   profile/main            (see SyncedProfileRepository)
 *   notebook/{word}         word snapshot, note, createdAt
 *   word_notes/{word}       custom meaning/examples/collocations/idioms, personal note
 *   word_progress/{word}    spaced-repetition state
 *   flashcard_collections/c{createdAt}   title, description, cards[]
 *   quiz_attempts/a{completedAt}         score and answers
 *   learning_activity/{activityId}       XP and study time (daily stats are rebuilt from these)
 *
 * Words, lessons and questions have device-local row ids, so documents are keyed by the word text or a timestamp.
 * On sign-in the cloud copy is pulled first (items missing on this device are added; for notes and progress the newer one wins),
 * then every later change in Room is mirrored to Firestore. Offline writes queue inside Firestore.
 *
 * [refresh] (pull-to-refresh) repeats the pull.
 *
 * Flashcard collections follow the cloud: deleting one leaves a marker document ({deleted: true}) instead of removing it, so other
 * devices delete theirs on the next pull, and a pull makes the cards of an existing collection match the cloud list. A device whose
 * flashcard pull failed does not upload its collections that session, or a stale copy could overwrite newer cloud data.
 * Known limits: every other collection still never deletes (a document removed on another device stays here, and comes back when
 * this device next starts), and notebook entries and notes already present are not updated from the cloud.
 */
@Singleton
class UserDataSync @Inject constructor(
    private val database: EngMateDatabase,
    private val content: FirestoreContent,
    private val session: UserSession,
    private val profile: SyncedProfileRepository,
    private val profileDao: UserProfileDao,
    private val vocabulary: VocabularyDao,
    private val notebook: NotebookDao,
    private val flashcards: FlashcardDao,
    private val study: StudyDao,
    private val attempts: QuizAttemptDao,
    private val questions: QuestionDao,
    private val lessons: GrammarLessonDao,
) : SyncRefresher, HandbookGuard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    // One pull at a time, whether it is the sign-in pull or a refresh.
    private val pullLock = Mutex()
    @Volatile private var lastPullAt = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        val firestore = content.firestore ?: return
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            session.user.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                if (uid == null) return@collectLatest
                try { run(firestore, uid) } catch (e: CancellationException) { throw e } catch (e: Exception) { FirestoreContent.log("user data sync", e) }
            }
        }
    }

    private suspend fun run(firestore: FirebaseFirestore, uid: String) = coroutineScope {
        safely("profile") { profile.pull(uid) }
        // Every row below points at the profile, so wait until it exists (the user may still be creating it).
        profileDao.observe(uid).filterNotNull().first()
        val root = firestore.collection("users").document(uid)
        fun col(name: String) = root.collection(name)

        val pullFailed = pullAll(uid, root)

        launch { mirror(col("notebook"), notebook.observe(uid).map { list -> list.associate { e ->
            key(e.word.word) to mapOf("word" to e.word.snap(), "note" to e.entry.note, "createdAt" to e.entry.createdAt) } }) }
        launch { mirror(col("word_notes"), notebook.observeAllWordNotes(uid).map { list -> list.mapNotNull { n ->
            vocabulary.getWord(n.wordId)?.let { w -> key(w.word) to mapOf("word" to w.snap(), "customMeaning" to n.customMeaning,
                "customExamples" to n.customExamples, "customCollocations" to n.customCollocations, "customIdioms" to n.customIdioms,
                "personalNote" to n.personalNote, "updatedAt" to n.updatedAt) } }.toMap() }) }
        launch { mirror(col("word_progress"), flashcards.observeAllProgress(uid).map { list -> list.mapNotNull { p ->
            vocabulary.getWord(p.wordId)?.let { w -> key(w.word) to mapOf("word" to w.snap(), "repetition" to p.repetition, "interval" to p.intervalDays,
                "easeFactor" to p.easeFactor, "lastReviewedAt" to p.lastReviewedAt, "nextReviewAt" to p.nextReviewAt, "status" to p.status.name) } }.toMap() }) }
        if (FLASHCARDS in pullFailed) Log.w(FirestoreContent.TAG, "Flashcard pull failed: not uploading collections this session")
        else launch { mirror(col("flashcard_collections"), combine(flashcards.observeCollections(uid), flashcards.observeAllCards(uid)) { cols, cards ->
            cols.associate { c -> "c${c.createdAt ?: c.id}" to mapOf("title" to c.title, "description" to c.description, "createdAt" to c.createdAt,
                "cards" to cards.filter { it.collectionId == c.id }.map { mapOf("word" to it.word, "meaning" to it.meaning, "example" to it.example, "phonetic" to it.phonetic) }) } },
            tombstones = true) }
        launch { mirror(col("quiz_attempts"), attempts.observeEntities(uid).map { list -> list.associate { a ->
            "a${a.completedAt}" to mapOf("topicId" to a.topicId, "lessonId" to a.lessonId, "isPlacement" to a.isPlacement, "correctCount" to a.correctCount,
                "totalQuestions" to a.totalQuestions, "xpEarned" to a.xpEarned, "completedAt" to a.completedAt, "updatedAt" to a.updatedAt,
                "answers" to attempts.answersFor(a.id).map { x -> mapOf("questionId" to x.questionId, "answerText" to x.answerText,
                    "questionSnapshot" to x.questionSnapshot, "selectedOptionSnapshot" to x.selectedOptionSnapshot,
                    "explanationSnapshot" to x.explanationSnapshot, "isCorrect" to x.isCorrect) }) } }) }
        launch { mirror(col("learning_activity"), study.observeActivities(uid, "0000-01-01", "9999-12-31").map { list -> list.associate { a ->
            a.activityId to mapOf("type" to a.activityType.name, "sourceId" to a.sourceId, "xpEarned" to a.xpEarned, "durationSeconds" to a.durationSeconds,
                "occurredAt" to a.occurredAt, "studyDate" to a.studyDate, "wordsLearned" to a.wordsLearned, "wordsReviewed" to a.wordsReviewed,
                "quizzesCompleted" to a.quizzesCompleted) } }) }
    }

    /** Returns false when [block] failed; the failure is logged, not thrown, so one broken collection does not stop the rest. */
    private suspend fun safely(what: String, block: suspend () -> Unit): Boolean =
        try { block(); true } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(FirestoreContent.TAG, "Pull $what failed", e); false }

    /** Pulls every personal collection once, in order. Returns the names of the ones that failed. */
    private suspend fun pullAll(uid: String, root: DocumentReference): Set<String> = pullLock.withLock {
        fun col(name: String) = root.collection(name)
        val failed = LinkedHashSet<String>()
        suspend fun pull(name: String, block: suspend () -> Unit) { if (!safely(name, block)) failed += name }
        pull("notebook") { pullNotebook(uid, col("notebook")) }
        pull("word_notes") { pullNotes(uid, col("word_notes")) }
        pull("word_progress") { pullProgress(uid, col("word_progress")) }
        pull(FLASHCARDS) { pullCollections(uid, col("flashcard_collections")) }
        pull("attempts") { pullAttempts(uid, col("quiz_attempts")) }
        pull("activity") { pullActivities(uid, col("learning_activity")) }
        lastPullAt = SystemClock.elapsedRealtime()
        failed
    }

    override suspend fun refresh(background: Boolean): RefreshResult {
        val firestore = content.firestore ?: return RefreshResult.FAILED
        val uid = session.user.first()?.uid ?: return RefreshResult.SIGNED_OUT
        // Before the sign-in pull has finished there is nothing to catch up on, and that pull is already on its way.
        if (background && lastPullAt == 0L) return RefreshResult.THROTTLED
        if (pullLock.isLocked || SystemClock.elapsedRealtime() - lastPullAt < MIN_REFRESH_GAP_MS) return RefreshResult.THROTTLED
        val root = firestore.collection("users").document(uid)
        // Offline, get() quietly answers from Firestore's local cache and looks successful; a one-document server read proves we are online.
        try { root.collection("notebook").limit(1).get(Source.SERVER).await() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w(FirestoreContent.TAG, "Refresh probe failed", e)
            return if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAVAILABLE) RefreshResult.OFFLINE else RefreshResult.FAILED
        }
        if (!safely("profile") { profile.pull(uid) }) return RefreshResult.FAILED
        // Every row points at the profile; a learner who has not created one yet has nothing to pull into.
        if (profileDao.observe(uid).first() == null) return RefreshResult.DONE
        return if (pullAll(uid, root).isEmpty()) RefreshResult.DONE else RefreshResult.FAILED
    }

    /**
     * One server read of this handbook's document before it is changed. Anything unclear (offline, slow, no document yet, a change of
     * ours still waiting to upload) lets the edit through, so editing keeps working offline; only a delete marker stops it.
     */
    override suspend fun deletedElsewhere(handbookId: Long): Boolean {
        val firestore = content.firestore ?: return false
        val uid = session.user.first()?.uid ?: return false
        val mine = flashcards.getCollection(uid, handbookId) ?: return false
        val doc = firestore.collection("users").document(uid).collection("flashcard_collections").document("c${mine.createdAt ?: mine.id}")
        val snapshot = try { withTimeoutOrNull(GUARD_TIMEOUT_MS) { doc.get(Source.SERVER).await() } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return false
        if (snapshot.metadata.hasPendingWrites() || snapshot.getBoolean("deleted") != true) return false
        flashcards.deleteCollection(uid, handbookId)
        return true
    }

    private companion object {
        const val MIN_REFRESH_GAP_MS = 30_000L
        const val GUARD_TIMEOUT_MS = 3_000L
        const val FLASHCARDS = "flashcards"
    }

    /**
     * Writes new and changed documents; removes documents whose local row disappeared while we were watching.
     * With [tombstones] the document is replaced by a {deleted: true} marker instead of being deleted, so other devices can see it was removed.
     */
    private suspend fun mirror(col: CollectionReference, desired: Flow<Docs>, tombstones: Boolean = false) {
        val pushed = HashMap<String, Map<String, Any?>>()
        val seen = HashSet<String>()
        desired.collect { now ->
            seen += now.keys
            now.forEach { (id, data) ->
                if (pushed[id] != data) { pushed[id] = data; col.document(id).set(data).addOnFailureListener { FirestoreContent.log("put ${col.id}/$id", it) } }
            }
            (seen - now.keys).forEach { id ->
                seen.remove(id); pushed.remove(id)
                val doc = col.document(id)
                (if (tombstones) doc.set(mapOf("deleted" to true, "deletedAt" to System.currentTimeMillis())) else doc.delete())
                    .addOnFailureListener { FirestoreContent.log("delete ${col.id}/$id", it) }
            }
        }
    }

    // ---- cloud -> Room ----

    private suspend fun pullNotebook(uid: String, col: CollectionReference) {
        // Read-then-write inside one transaction, so an edit made on this device while we pull cannot be overwritten in between.
        for (doc in col.get().await().documents) runCatching { database.withTransaction {
            val wordId = ensureWord(doc.get("word")) ?: return@withTransaction
            val note = doc.getString("note")
            val mine = notebook.get(uid, wordId)
            if (mine == null) notebook.add(NotebookWordEntity(0, uid, wordId, note, doc.getLong("createdAt")))
            else if (mine.note == null && note != null) notebook.updateNote(uid, wordId, note)
        } }
    }

    private suspend fun pullNotes(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching { database.withTransaction {
            val wordId = ensureWord(doc.get("word")) ?: return@withTransaction
            val updated = doc.getLong("updatedAt") ?: 0L
            val mine = notebook.getWordNote(uid, wordId)
            if (mine == null || updated > (mine.updatedAt ?: 0L)) notebook.saveWordNote(WordNoteEntity(0, uid, wordId, doc.getString("customMeaning"),
                strings(doc.get("customExamples")), strings(doc.get("customCollocations")), strings(doc.get("customIdioms")), doc.getString("personalNote"), doc.getLong("updatedAt")))
        } }
    }

    private suspend fun pullProgress(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching { database.withTransaction {
            val wordId = ensureWord(doc.get("word")) ?: return@withTransaction
            val reviewed = doc.getLong("lastReviewedAt")
            val mine = flashcards.getProgress(uid, wordId)
            if (mine == null || (reviewed ?: 0L) > (mine.lastReviewedAt ?: 0L)) flashcards.saveProgress(WordProgressEntity(0, uid, wordId,
                (doc.getLong("repetition") ?: 0L).toInt(), (doc.getLong("interval") ?: 1L).toInt(), doc.getDouble("easeFactor") ?: 2.5,
                reviewed, doc.getLong("nextReviewAt") ?: 0L, WordLearningStatus.valueOf(doc.getString("status") ?: "NEW")))
        } }
    }

    /** The cloud is the latest copy of a collection: a marker deletes it here, and an existing one is made to match the cloud. */
    private suspend fun pullCollections(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching { database.withTransaction {
            // A change made on this device is still waiting to be sent. The snapshot already contains it, so there is nothing newer to take.
            if (doc.metadata.hasPendingWrites()) return@withTransaction
            val key = doc.id.removePrefix("c").toLongOrNull() ?: return@withTransaction
            val mine = flashcards.collectionsOf(uid).firstOrNull { (it.createdAt ?: it.id) == key }
            if (doc.getBoolean("deleted") == true) { mine?.let { flashcards.deleteCollection(uid, it.id) }; return@withTransaction }
            val title = doc.getString("title") ?: return@withTransaction
            val description = doc.getString("description")
            val raw = (doc.get("cards") as? List<*>) ?: return@withTransaction
            val cards = raw.mapNotNull { c -> (c as? Map<*, *>)?.let { m ->
                CardData(m["word"] as? String ?: return@let null, m["meaning"] as? String ?: return@let null, m["example"] as? String, m["phonetic"] as? String)
            } }
            // A malformed card would look like a removed one; leave this collection alone rather than delete cards by mistake.
            if (cards.size != raw.size) return@withTransaction

            if (mine == null) {
                val created = doc.getLong("createdAt") ?: return@withTransaction
                val id = flashcards.insertCollection(FlashcardCollectionEntity(0, uid, title, description, created))
                cards.forEach { flashcards.addCard(uid, FlashcardEntity(0, id, it.word, it.meaning, it.example, it.phonetic)) }
                return@withTransaction
            }
            if (mine.title != title || mine.description != description) flashcards.updateCollection(uid, mine.id, title, description)
            val plan = planCardSync(flashcards.cardsOf(mine.id).map { LocalCard(it.id, CardData(it.word, it.meaning, it.example, it.phonetic)) }, cards)
            plan.delete.forEach { flashcards.deleteCard(uid, it) }
            plan.update.forEach { (id, c) -> flashcards.updateCard(uid, id, c.word, c.meaning, c.example, c.phonetic) }
            plan.add.forEach { flashcards.addCard(uid, FlashcardEntity(0, mine.id, it.word, it.meaning, it.example, it.phonetic)) }
        } }
    }

    private suspend fun pullAttempts(uid: String, col: CollectionReference) {
        val have = attempts.observeEntities(uid).first().map { it.completedAt }.toSet()
        for (doc in col.get().await().documents) runCatching {
            val completed = doc.getLong("completedAt") ?: return@runCatching
            if (completed in have) return@runCatching
            val lessonId = doc.getLong("lessonId")?.takeIf { lessons.get(it) != null }
            val topicId = doc.getLong("topicId")?.takeIf { vocabulary.getTopic(it) != null }
            val answers = (doc.get("answers") as? List<*>).orEmpty().mapNotNull { a ->
                val m = a as? Map<*, *> ?: return@mapNotNull null
                val question = questions.get((m["questionId"] as? Number)?.toLong() ?: return@mapNotNull null) ?: return@mapNotNull null
                val selected = m["selectedOptionSnapshot"] as? String
                AttemptAnswerEntity(0, 0, question.question.id, question.options.firstOrNull { it.content == selected }?.id, m["answerText"] as? String,
                    m["questionSnapshot"] as? String ?: "", selected, m["explanationSnapshot"] as? String, m["isCorrect"] as? Boolean ?: false)
            }
            attempts.saveAttempt(QuizAttemptEntity(0, uid, topicId, lessonId, doc.getBoolean("isPlacement") ?: false,
                (doc.getLong("correctCount") ?: 0L).toInt(), (doc.getLong("totalQuestions") ?: 0L).toInt(), (doc.getLong("xpEarned") ?: 0L).toInt(),
                completed, doc.getLong("updatedAt")), answers)
        }
    }

    private suspend fun pullActivities(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching {
            if (study.getActivity(uid, doc.id) != null) return@runCatching
            study.record(LearningActivityEntity(doc.id, uid, ActivityType.valueOf(doc.getString("type") ?: return@runCatching), doc.getString("sourceId"),
                (doc.getLong("xpEarned") ?: 0L).toInt(), (doc.getLong("durationSeconds") ?: 0L).toInt(), doc.getLong("occurredAt") ?: return@runCatching,
                doc.getString("studyDate") ?: return@runCatching, (doc.getLong("wordsLearned") ?: 0L).toInt(), (doc.getLong("wordsReviewed") ?: 0L).toInt(),
                (doc.getLong("quizzesCompleted") ?: 0L).toInt()))
        }
    }

    /** Finds the shared word row by its text, creating it from the cloud snapshot when this device has never seen the word. */
    private suspend fun ensureWord(snapshot: Any?): Long? {
        val m = snapshot as? Map<*, *> ?: return null
        val text = (m["word"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        vocabulary.findWord(text)?.let { return it.id }
        return vocabulary.insertWord(WordEntity(0, null, text, m["phonetic"] as? String, m["partOfSpeech"] as? String, m["meaningEn"] as? String ?: "",
            m["meaningVi"] as? String, m["exampleSentence"] as? String, m["exampleMeaning"] as? String, m["audioUrl"] as? String,
            strings(m["collocations"]), strings(m["idioms"]), m["isCached"] as? Boolean ?: false, System.currentTimeMillis()))
    }

    private fun strings(value: Any?): List<String> = (value as? List<*>).orEmpty().filterIsInstance<String>()
    private fun key(word: String) = word.trim().lowercase().replace('/', '_')
    private fun WordEntity.snap(): Map<String, Any?> = mapOf("word" to word, "phonetic" to phonetic, "partOfSpeech" to partOfSpeech, "meaningEn" to meaningEn,
        "meaningVi" to meaningVi, "exampleSentence" to exampleSentence, "exampleMeaning" to exampleMeaning, "audioUrl" to audioUrl,
        "collocations" to collocations, "idioms" to idioms, "isCached" to isCached)
}
