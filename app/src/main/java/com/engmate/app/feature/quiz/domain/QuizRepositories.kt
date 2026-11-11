package com.engmate.app.feature.quiz.domain

import kotlinx.coroutines.flow.Flow

interface QuestionRepository {
    fun observeQuestions(): Flow<List<Question>>
    fun observeQuestion(id: Long): Flow<Question?>
    suspend fun questionsForLesson(lessonId: Long): List<Question>
    suspend fun placementQuestions(): List<Question>
    /** Saves the question and its options atomically. Throws [InvalidQuestion]. */
    suspend fun create(input: QuestionInput): Long
    /** Updates the question and replaces all of its options. Throws [InvalidQuestion], [QuestionInUse], [QuestionMissing]. */
    suspend fun update(id: Long, input: QuestionInput)
    /** Throws [QuestionInUse] or [QuestionMissing]. */
    suspend fun delete(id: Long)
}

data class AnswerDraft(val questionId: Long, val selectedOptionId: Long? = null, val answerText: String? = null)

data class AttemptDraft(
    val topicId: Long? = null,
    val lessonId: Long? = null,
    val isPlacement: Boolean = false,
    val answers: List<AnswerDraft>,
)

data class AttemptResult(val attemptId: Long, val correctCount: Int, val totalQuestions: Int, val xpEarned: Int, val level: String?)

data class AttemptSummary(
    val id: Long,
    val title: String,
    val isPlacement: Boolean,
    val correctCount: Int,
    val totalQuestions: Int,
    val xpEarned: Int,
    val completedAt: Long,
)

data class AnsweredQuestion(
    val questionId: Long,
    val question: String,
    val answerType: AnswerType,
    /** The chosen option or typed text, null when skipped. */
    val given: String?,
    val correctAnswers: List<String>,
    val explanation: String?,
    val isCorrect: Boolean,
)

data class AttemptDetail(val summary: AttemptSummary, val answers: List<AnsweredQuestion>)

interface QuizAttemptRepository {
    fun observeHistory(uid: String): Flow<List<AttemptSummary>>
    fun observeAttempt(uid: String, id: Long): Flow<AttemptDetail?>
    /**
     * Grades the answers, stores the attempt and all answers in one transaction (XP = 10 × correct).
     * A placement attempt also updates the profile's english level.
     * Throws [InvalidAttempt], [ProfileRequired] or [AttemptSessionRequired].
     */
    suspend fun saveAttempt(uid: String, draft: AttemptDraft): AttemptResult
    /** Deletes the attempt; its answers cascade. Throws [AttemptMissing]. */
    suspend fun delete(uid: String, id: Long)
}

class InvalidAttempt(message: String) : IllegalArgumentException(message)
class AttemptMissing : IllegalStateException("Lượt làm bài không còn tồn tại")
class ProfileRequired : IllegalStateException("Hãy tạo hồ sơ cá nhân trước khi lưu kết quả")
class AttemptSessionRequired : IllegalStateException("Bạn cần đăng nhập lại")

const val PLACEMENT_TITLE = "Kiểm tra trình độ"
