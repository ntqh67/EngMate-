package com.engmate.app.feature.grammar.domain

import kotlinx.coroutines.flow.Flow

interface GrammarRepository {
    fun observeLessons(): Flow<List<GrammarLesson>>
    fun observeLesson(id: Long): Flow<GrammarLesson?>
    /** Returns the new lesson id. Throws [InvalidLesson]. */
    suspend fun create(input: LessonInput): Long
    /** Throws [InvalidLesson] or [LessonMissing]. */
    suspend fun update(id: Long, input: LessonInput)
    /** Deletes the lesson and its questions. Throws [LessonInUse] if any of them was answered, or [LessonMissing]. */
    suspend fun delete(id: Long)
}
