package com.engmate.app.sync

import android.util.Log
import com.engmate.app.core.database.ActivityType
import com.engmate.app.core.database.WordLearningStatus
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
import com.google.firebase.firestore.FirebaseFirestore
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
 */
@Singleton
class UserDataSync @Inject constructor(
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

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

        safely("notebook") { pullNotebook(uid, col("notebook")) }
        safely("word_notes") { pullNotes(uid, col("word_notes")) }
        safely("word_progress") { pullProgress(uid, col("word_progress")) }
        safely("flashcards") { pullCollections(uid, col("flashcard_collections")) }
        safely("attempts") { pullAttempts(uid, col("quiz_attempts")) }
        safely("activity") { pullActivities(uid, col("learning_activity")) }

        launch { mirror(col("notebook"), notebook.observe(uid).map { list -> list.associate { e ->
            key(e.word.word) to mapOf("word" to e.word.snap(), "note" to e.entry.note, "createdAt" to e.entry.createdAt) } }) }
        launch { mirror(col("word_notes"), notebook.observeAllWordNotes(uid).map { list -> list.mapNotNull { n ->
            vocabulary.getWord(n.wordId)?.let { w -> key(w.word) to mapOf("word" to w.snap(), "customMeaning" to n.customMeaning,
                "customExamples" to n.customExamples, "customCollocations" to n.customCollocations, "customIdioms" to n.customIdioms,
                "personalNote" to n.personalNote, "updatedAt" to n.updatedAt) } }.toMap() }) }
        launch { mirror(col("word_progress"), flashcards.observeAllProgress(uid).map { list -> list.mapNotNull { p ->
            vocabulary.getWord(p.wordId)?.let { w -> key(w.word) to mapOf("word" to w.snap(), "repetition" to p.repetition, "interval" to p.intervalDays,
                "easeFactor" to p.easeFactor, "lastReviewedAt" to p.lastReviewedAt, "nextReviewAt" to p.nextReviewAt, "status" to p.status.name) } }.toMap() }) }
        launch { mirror(col("flashcard_collections"), combine(flashcards.observeCollections(uid), flashcards.observeAllCards(uid)) { cols, cards ->
            cols.associate { c -> "c${c.createdAt ?: c.id}" to mapOf("title" to c.title, "description" to c.description, "createdAt" to c.createdAt,
                "cards" to cards.filter { it.collectionId == c.id }.map { mapOf("word" to it.word, "meaning" to it.meaning, "example" to it.example, "phonetic" to it.phonetic) }) } }) }
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

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(FirestoreContent.TAG, "Pull $what failed", e) }
    }

    /** Writes new and changed documents; deletes documents whose local row disappeared while we were watching. */
    private suspend fun mirror(col: CollectionReference, desired: Flow<Docs>) {
        val pushed = HashMap<String, Map<String, Any?>>()
        val seen = HashSet<String>()
        desired.collect { now ->
            seen += now.keys
            now.forEach { (id, data) ->
                if (pushed[id] != data) { pushed[id] = data; col.document(id).set(data).addOnFailureListener { FirestoreContent.log("put ${col.id}/$id", it) } }
            }
            (seen - now.keys).forEach { id ->
                seen.remove(id); pushed.remove(id)
                col.document(id).delete().addOnFailureListener { FirestoreContent.log("delete ${col.id}/$id", it) }
            }
        }
    }

    // ---- cloud -> Room ----

    private suspend fun pullNotebook(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching {
            val wordId = ensureWord(doc.get("word")) ?: return@runCatching
            val note = doc.getString("note")
            val mine = notebook.get(uid, wordId)
            if (mine == null) notebook.add(NotebookWordEntity(0, uid, wordId, note, doc.getLong("createdAt")))
            else if (mine.note == null && note != null) notebook.updateNote(uid, wordId, note)
        }
    }

    private suspend fun pullNotes(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching {
            val wordId = ensureWord(doc.get("word")) ?: return@runCatching
            val updated = doc.getLong("updatedAt") ?: 0L
            val mine = notebook.getWordNote(uid, wordId)
            if (mine == null || updated > (mine.updatedAt ?: 0L)) notebook.saveWordNote(WordNoteEntity(0, uid, wordId, doc.getString("customMeaning"),
                strings(doc.get("customExamples")), strings(doc.get("customCollocations")), strings(doc.get("customIdioms")), doc.getString("personalNote"), doc.getLong("updatedAt")))
        }
    }

    private suspend fun pullProgress(uid: String, col: CollectionReference) {
        for (doc in col.get().await().documents) runCatching {
            val wordId = ensureWord(doc.get("word")) ?: return@runCatching
            val reviewed = doc.getLong("lastReviewedAt")
            val mine = flashcards.getProgress(uid, wordId)
            if (mine == null || (reviewed ?: 0L) > (mine.lastReviewedAt ?: 0L)) flashcards.saveProgress(WordProgressEntity(0, uid, wordId,
                (doc.getLong("repetition") ?: 0L).toInt(), (doc.getLong("interval") ?: 1L).toInt(), doc.getDouble("easeFactor") ?: 2.5,
                reviewed, doc.getLong("nextReviewAt") ?: 0L, WordLearningStatus.valueOf(doc.getString("status") ?: "NEW")))
        }
    }

    private suspend fun pullCollections(uid: String, col: CollectionReference) {
        val have = flashcards.collectionsOf(uid).map { it.createdAt ?: it.id }.toSet()
        for (doc in col.get().await().documents) runCatching {
            val created = doc.getLong("createdAt")
            if ((created ?: return@runCatching) in have) return@runCatching
            val id = flashcards.insertCollection(FlashcardCollectionEntity(0, uid, doc.getString("title") ?: return@runCatching, doc.getString("description"), created))
            (doc.get("cards") as? List<*>).orEmpty().forEach { c ->
                val m = c as? Map<*, *> ?: return@forEach
                flashcards.addCard(uid, FlashcardEntity(0, id, m["word"] as? String ?: return@forEach, m["meaning"] as? String ?: return@forEach,
                    m["example"] as? String, m["phonetic"] as? String))
            }
        }
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
