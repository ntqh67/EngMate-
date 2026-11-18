package com.engmate.app.feature.grammar.data

import android.database.sqlite.SQLiteConstraintException
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.grammar.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGrammarRepository @Inject constructor(
    private val dao: GrammarLessonDao, private val clock: Clock,
) : GrammarRepository {
    override fun observeLessons(): Flow<List<GrammarLesson>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeLesson(id: Long): Flow<GrammarLesson?> = dao.observe(id).map { it?.toDomain() }

    private fun validate(input: LessonInput): LessonInput {
        val errors = LessonValidator.errors(input)
        if (errors.isNotEmpty()) throw InvalidLesson(errors)
        return input.normalized()
    }

    override suspend fun create(input: LessonInput): Long {
        val value = validate(input)
        return dao.insert(GrammarLessonEntity(title = value.title, level = value.level, summary = value.summary.ifEmpty { null },
            content = value.content, examples = value.examples, orderIndex = dao.nextOrderIndex(), updatedAt = clock.millis()))
    }

    override suspend fun update(id: Long, input: LessonInput) {
        val value = validate(input)
        val current = dao.get(id) ?: throw LessonMissing()
        if (dao.update(current.copy(title = value.title, level = value.level, summary = value.summary.ifEmpty { null },
                content = value.content, examples = value.examples, updatedAt = clock.millis())) == 0) throw LessonMissing()
    }

    override suspend fun delete(id: Long) {
        if (dao.usageCount(id) > 0) throw LessonInUse()
        val deleted = try { dao.deleteWithQuestions(id) } catch (_: SQLiteConstraintException) { throw LessonInUse() }
        if (deleted == 0) throw LessonMissing()
    }
}
