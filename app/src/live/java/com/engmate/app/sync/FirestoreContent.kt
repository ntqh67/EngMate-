package com.engmate.app.sync

import android.content.Context
import android.util.Log
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.grammar.domain.LessonInput
import com.engmate.app.feature.grammar.domain.LessonValidator
import com.engmate.app.feature.quiz.data.local.QuestionEntity
import com.engmate.app.feature.quiz.data.local.QuestionOptionEntity
import com.engmate.app.feature.quiz.data.local.QuestionWithOptions
import com.engmate.app.feature.quiz.domain.AnswerType
import com.engmate.app.feature.quiz.domain.OptionInput
import com.engmate.app.feature.quiz.domain.QuestionInput
import com.engmate.app.feature.quiz.domain.QuestionValidator
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Cloud Firestore layout (document id = the Room id as a decimal string, e.g. "12"):
 *
 *   grammar_lessons/{id}  title, level, summary, content, examples[{en, vi}], orderIndex, updatedAt
 *   questions/{id}        lessonId, topicId, isPlacement, answerType, content, explanation,
 *                         options[{content, isCorrect}], createdAt
 *
 * Documents that fail the app's validators are ignored on read, so a typo in the Firebase console
 * cannot put broken content on users' phones.
 */
@Singleton
class FirestoreContent @Inject constructor(@ApplicationContext context: Context) {
    /** Null when the build has no google-services.json; the app then keeps working from Room alone. */
    val firestore: FirebaseFirestore? =
        if (FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null) FirebaseFirestore.getInstance()
        else null

    fun lessons() = firestore?.collection(LESSONS)
    fun questions() = firestore?.collection(QUESTIONS)

    // Writes are not awaited: Firestore queues them on the device and sends them when the network is back.
    fun putLesson(lesson: GrammarLessonEntity) {
        lessons()?.document(lesson.id.toString())?.set(lesson.toRemote())?.addOnFailureListener { log("put lesson ${lesson.id}", it) }
    }

    fun putQuestion(question: QuestionWithOptions) {
        questions()?.document(question.question.id.toString())?.set(question.toRemote())
            ?.addOnFailureListener { log("put question ${question.question.id}", it) }
    }

    fun deleteLesson(id: Long, questionIds: List<Long>) {
        val db = firestore ?: return
        db.batch().apply {
            questionIds.forEach { delete(db.collection(QUESTIONS).document(it.toString())) }
            delete(db.collection(LESSONS).document(id.toString()))
        }.commit().addOnFailureListener { log("delete lesson $id", it) }
    }

    fun deleteQuestion(id: Long) {
        questions()?.document(id.toString())?.delete()?.addOnFailureListener { log("delete question $id", it) }
    }

    /** Uploads everything in one or more batches (Firestore allows 500 writes per batch). */
    fun uploadAll(lessons: List<GrammarLessonEntity>, questions: List<QuestionWithOptions>) {
        val db = firestore ?: return
        val writes = lessons.map { db.collection(LESSONS).document(it.id.toString()) to it.toRemote() } +
            questions.map { db.collection(QUESTIONS).document(it.question.id.toString()) to it.toRemote() }
        writes.chunked(400).forEach { chunk ->
            db.batch().apply { chunk.forEach { (ref, data) -> set(ref, data) } }.commit()
                .addOnFailureListener { log("initial upload", it) }
        }
    }

    companion object {
        const val LESSONS = "grammar_lessons"
        const val QUESTIONS = "questions"
        const val TAG = "FirestoreContent"
        fun log(what: String, error: Throwable) = Log.w(TAG, "Firestore $what failed", error)
    }
}

/** Emits every snapshot of the query; the flow fails with the listener's error (e.g. PERMISSION_DENIED). */
fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

// ---- Room -> Firestore ----

fun GrammarLessonEntity.toRemote(): Map<String, Any?> = mapOf(
    "title" to title, "level" to level, "summary" to summary, "content" to content,
    "examples" to examples.map { mapOf("en" to it.en, "vi" to it.vi) },
    "orderIndex" to orderIndex, "updatedAt" to updatedAt,
)

fun QuestionWithOptions.toRemote(): Map<String, Any?> = mapOf(
    "lessonId" to question.lessonId, "topicId" to question.topicId, "isPlacement" to question.isPlacement,
    "answerType" to question.answerType.name, "content" to question.content, "explanation" to question.explanation,
    "options" to options.sortedWith(compareBy({ it.orderIndex }, { it.id }))
        .map { mapOf("content" to it.content, "isCorrect" to it.isCorrect) },
    "createdAt" to question.createdAt,
)

// ---- Firestore -> Room (null = invalid document, skip it) ----

fun DocumentSnapshot.toLessonEntity(): GrammarLessonEntity? {
    val id = id.toLongOrNull() ?: return null
    val examples = (get("examples") as? List<*>).orEmpty().mapNotNull { item ->
        (item as? Map<*, *>)?.let { GrammarExample(it["en"] as? String ?: "", it["vi"] as? String ?: "") }
    }
    val input = LessonInput(getString("title").orEmpty(), getString("level").orEmpty(), getString("summary").orEmpty(),
        getString("content").orEmpty(), examples)
    if (LessonValidator.errors(input).isNotEmpty()) return null
    val value = input.normalized()
    return GrammarLessonEntity(id, value.title, value.level, value.summary.ifEmpty { null }, value.content, value.examples,
        orderIndex = getLong("orderIndex")?.toInt() ?: 0, updatedAt = getLong("updatedAt"))
}

/** Returns the question row and its options (option ids are assigned by Room on insert). */
fun DocumentSnapshot.toQuestion(): Pair<QuestionEntity, List<QuestionOptionEntity>>? {
    val id = id.toLongOrNull() ?: return null
    val type = runCatching { AnswerType.valueOf(getString("answerType").orEmpty()) }.getOrNull() ?: return null
    val options = (get("options") as? List<*>).orEmpty().mapNotNull { item ->
        (item as? Map<*, *>)?.let { OptionInput(it["content"] as? String ?: "", it["isCorrect"] as? Boolean ?: false) }
    }
    val input = QuestionInput(topicId = getLong("topicId"), lessonId = getLong("lessonId"), isPlacement = getBoolean("isPlacement") ?: false,
        answerType = type, content = getString("content").orEmpty(), explanation = getString("explanation").orEmpty(), options = options)
    if (QuestionValidator.errors(input).isNotEmpty()) return null
    val value = input.normalized()
    val question = QuestionEntity(id, value.topicId, value.lessonId, value.isPlacement, value.answerType, value.content,
        value.explanation.ifEmpty { null }, getLong("createdAt"))
    return question to value.options.mapIndexed { i, o ->
        QuestionOptionEntity(questionId = id, content = o.content, isCorrect = o.isCorrect, orderIndex = i)
    }
}
