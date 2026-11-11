package com.engmate.app.feature.quiz.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizAttemptDao {
    @Query("""SELECT a.*, l.title AS lesson_title FROM quiz_attempts a
              LEFT JOIN grammar_lessons l ON l.id = a.lesson_id
              WHERE a.user_id = :uid ORDER BY a.completed_at DESC, a.id DESC""")
    fun observeForUser(uid: String): Flow<List<AttemptRow>>

    @Transaction
    @Query("""SELECT a.*, l.title AS lesson_title FROM quiz_attempts a
              LEFT JOIN grammar_lessons l ON l.id = a.lesson_id
              WHERE a.id = :id AND a.user_id = :uid""")
    fun observeWithAnswers(uid: String, id: Long): Flow<AttemptWithAnswers?>

    @Insert suspend fun insertAttempt(attempt: QuizAttemptEntity): Long
    @Insert suspend fun insertAnswers(answers: List<AttemptAnswerEntity>)

    @Query("SELECT COUNT(*) FROM attempt_answers WHERE attempt_id = :attemptId")
    suspend fun answerCount(attemptId: Long): Int

    /** Answers cascade. */
    @Query("DELETE FROM quiz_attempts WHERE id = :id AND user_id = :uid")
    suspend fun delete(uid: String, id: Long): Int

    @Transaction
    suspend fun saveAttempt(attempt: QuizAttemptEntity, answers: List<AttemptAnswerEntity>): Long {
        val id = insertAttempt(attempt.copy(id = 0))
        insertAnswers(answers.map { it.copy(id = 0, attemptId = id) })
        return id
    }
}
