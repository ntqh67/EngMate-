package com.engmate.app.feature.quiz.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class QuestionWithOptions(
    @Embedded val question: QuestionEntity,
    @Relation(parentColumn = "id", entityColumn = "question_id") val options: List<QuestionOptionEntity>,
)
data class AttemptWithAnswers(
    @Embedded val attempt: QuizAttemptEntity,
    @Relation(parentColumn = "id", entityColumn = "attempt_id") val answers: List<AttemptAnswerEntity>,
)

@Dao
abstract class QuizDao {
    @Insert protected abstract suspend fun insertQuestion(question: QuestionEntity): Long
    @Update protected abstract suspend fun updateQuestion(question: QuestionEntity): Int
    @Insert protected abstract suspend fun insertOptions(options: List<QuestionOptionEntity>)
    @Query("DELETE FROM question_options WHERE question_id = :id") protected abstract suspend fun deleteOptions(id: Long)
    @Transaction @Query("SELECT * FROM questions WHERE id = :id")
    abstract suspend fun getQuestion(id: Long): QuestionWithOptions?
    @Transaction @Query("SELECT * FROM questions WHERE topic_id = :topicId AND is_placement = 0 ORDER BY id")
    abstract fun observeTopicQuestions(topicId: Long): Flow<List<QuestionWithOptions>>
    @Transaction @Query("SELECT * FROM questions WHERE lesson_id = :lessonId AND is_placement = 0 ORDER BY id")
    abstract fun observeLessonQuestions(lessonId: Long): Flow<List<QuestionWithOptions>>
    @Transaction @Query("SELECT * FROM questions WHERE is_placement = 1 ORDER BY id")
    abstract fun observePlacementQuestions(): Flow<List<QuestionWithOptions>>
    @Query("DELETE FROM questions WHERE id = :id") abstract suspend fun deleteQuestion(id: Long): Int

    // Replace options atomically; questions used in history cannot have options removed.
    @Transaction
    open suspend fun saveQuestion(question: QuestionEntity, options: List<QuestionOptionEntity>): Long {
        require(question.content.isNotBlank())
        require(listOfNotNull(question.topicId, question.lessonId).size + (if (question.isPlacement) 1 else 0) == 1) {
            "Question needs exactly one topic, lesson or placement scope"
        }
        when (question.answerType) {
            com.engmate.app.core.database.AnswerType.SINGLE_CHOICE -> {
                require(options.size >= 2 && options.count { it.isCorrect } == 1)
                require(options.all { it.content.isNotBlank() })
            }
            com.engmate.app.core.database.AnswerType.TEXT -> {
                require(options.isEmpty() && !question.correctAnswer.isNullOrBlank())
            }
        }
        val id = if (question.id == 0L) insertQuestion(question) else {
            check(updateQuestion(question) == 1) { "Question not found" }
            deleteOptions(question.id)
            question.id
        }
        insertOptions(options.mapIndexed { order, option -> option.copy(id = 0, questionId = id, orderIndex = order) })
        return id
    }

    @Insert protected abstract suspend fun insertAttempt(attempt: QuizAttemptEntity): Long
    @Insert protected abstract suspend fun insertAnswers(answers: List<AttemptAnswerEntity>)
    @Transaction @Query("SELECT * FROM quiz_attempts WHERE user_uid = :uid AND id = :id")
    abstract suspend fun getAttempt(uid: String, id: Long): AttemptWithAnswers?
    @Query("SELECT * FROM quiz_attempts WHERE user_uid = :uid ORDER BY completed_at DESC, id DESC")
    abstract fun observeAttempts(uid: String): Flow<List<QuizAttemptEntity>>
    @Query("DELETE FROM quiz_attempts WHERE user_uid = :uid AND id = :id")
    abstract suspend fun deleteAttempt(uid: String, id: Long): Int

    // Completed attempts are immutable; a retake creates a new attempt.
    @Transaction
    open suspend fun saveAttempt(attempt: QuizAttemptEntity, answers: List<AttemptAnswerEntity>): Long {
        require(attempt.totalQuestions > 0 && answers.size == attempt.totalQuestions)
        require(attempt.correctCount == answers.count { it.isCorrect } && attempt.xpEarned >= 0)
        require(answers.map { it.questionId }.distinct().size == answers.size)
        require(listOfNotNull(attempt.topicId, attempt.lessonId).size + (if (attempt.isPlacement) 1 else 0) == 1)
        val snapshots = answers.map { answer ->
            val item = requireNotNull(getQuestion(answer.questionId)) { "Question not found" }
            val q = item.question
            require(q.topicId == attempt.topicId && q.lessonId == attempt.lessonId && q.isPlacement == attempt.isPlacement)
            val selected = answer.selectedOptionId?.let { id ->
                requireNotNull(item.options.find { it.id == id }) { "Option belongs to another question" }
            }
            when (q.answerType) {
                com.engmate.app.core.database.AnswerType.SINGLE_CHOICE -> require(selected != null && answer.answerText == null)
                com.engmate.app.core.database.AnswerType.TEXT -> require(selected == null && !answer.answerText.isNullOrBlank())
            }
            answer.copy(id = 0, questionSnapshot = q.content, selectedOptionSnapshot = selected?.content,
                explanationSnapshot = q.explanation)
        }
        val id = insertAttempt(attempt)
        insertAnswers(snapshots.map { it.copy(attemptId = id) })
        return id
    }
}
