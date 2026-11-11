package com.engmate.app.feature.grammar.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GrammarLessonDao {
    @Query("SELECT * FROM grammar_lessons ORDER BY order_index, id")
    fun observeAll(): Flow<List<GrammarLessonEntity>>

    @Query("SELECT * FROM grammar_lessons WHERE id = :id")
    fun observe(id: Long): Flow<GrammarLessonEntity?>

    @Query("SELECT * FROM grammar_lessons WHERE id = :id")
    suspend fun get(id: Long): GrammarLessonEntity?

    @Query("SELECT COALESCE(MAX(order_index), -1) + 1 FROM grammar_lessons")
    suspend fun nextOrderIndex(): Int

    @Insert
    suspend fun insert(lesson: GrammarLessonEntity): Long

    @Update
    suspend fun update(lesson: GrammarLessonEntity): Int

    /** Questions of this lesson that appear in someone's attempt, plus attempts pointing at the lesson. */
    @Query("""SELECT (SELECT COUNT(*) FROM attempt_answers aa JOIN questions q ON q.id = aa.question_id WHERE q.lesson_id = :id)
              + (SELECT COUNT(*) FROM quiz_attempts WHERE lesson_id = :id)""")
    suspend fun usageCount(id: Long): Int

    @Query("DELETE FROM questions WHERE lesson_id = :id")
    suspend fun deleteQuestionsOf(id: Long): Int

    @Query("DELETE FROM grammar_lessons WHERE id = :id")
    suspend fun deleteLesson(id: Long): Int

    /** Options cascade from questions. Returns 0 when the lesson is missing. */
    @Transaction
    suspend fun deleteWithQuestions(id: Long): Int {
        deleteQuestionsOf(id)
        return deleteLesson(id)
    }
}
