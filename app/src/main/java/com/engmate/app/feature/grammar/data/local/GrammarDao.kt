package com.engmate.app.feature.grammar.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GrammarDao {
    @Insert suspend fun insert(lesson: GrammarLessonEntity): Long
    @Update suspend fun update(lesson: GrammarLessonEntity): Int
    @Query("DELETE FROM grammar_lessons WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("SELECT * FROM grammar_lessons WHERE id = :id") suspend fun get(id: Long): GrammarLessonEntity?
    @Query("SELECT * FROM grammar_lessons WHERE :level IS NULL OR level = :level ORDER BY order_index, id")
    fun observeLessons(level: String? = null): Flow<List<GrammarLessonEntity>>
}
