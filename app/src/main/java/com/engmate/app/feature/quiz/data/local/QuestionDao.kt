package com.engmate.app.feature.quiz.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Transaction
    @Query("SELECT * FROM questions ORDER BY id DESC")
    fun observeAll(): Flow<List<QuestionWithOptions>>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :id")
    fun observe(id: Long): Flow<QuestionWithOptions?>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun get(id: Long): QuestionWithOptions?

    @Transaction
    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT * FROM questions WHERE lesson_id = :lessonId ORDER BY id")
    suspend fun forLesson(lessonId: Long): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT * FROM questions WHERE is_placement = 1 ORDER BY id LIMIT :limit")
    suspend fun placement(limit: Int): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT * FROM grammar_lessons WHERE id = :lessonId")
    fun observeLessonWithQuestions(lessonId: Long): Flow<LessonWithQuestions?>

    @Query("SELECT EXISTS(SELECT 1 FROM attempt_answers WHERE question_id = :id)")
    suspend fun isAnswered(id: Long): Boolean

    @Insert suspend fun insertQuestion(question: QuestionEntity): Long
    @Update suspend fun updateQuestion(question: QuestionEntity): Int
    @Insert suspend fun insertOptions(options: List<QuestionOptionEntity>)

    @Query("DELETE FROM question_options WHERE question_id = :questionId")
    suspend fun deleteOptions(questionId: Long)

    /** Options cascade. */
    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Transaction
    suspend fun insertWithOptions(question: QuestionEntity, options: List<QuestionOptionEntity>): Long {
        val id = insertQuestion(question.copy(id = 0))
        insertOptions(options.map { it.copy(id = 0, questionId = id) })
        return id
    }

    /** Updates the question row and replaces all of its options. Returns 0 if the question is missing. */
    @Transaction
    suspend fun replaceWithOptions(question: QuestionEntity, options: List<QuestionOptionEntity>): Int {
        if (updateQuestion(question) == 0) return 0
        deleteOptions(question.id)
        insertOptions(options.map { it.copy(id = 0, questionId = question.id) })
        return 1
    }
}
