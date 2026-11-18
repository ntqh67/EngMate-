package com.engmate.app.feature.quiz.data

import android.database.sqlite.SQLiteConstraintException
import com.engmate.app.feature.quiz.data.local.*
import com.engmate.app.feature.quiz.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomQuestionRepository @Inject constructor(
    private val dao: QuestionDao, private val clock: Clock,
) : QuestionRepository {
    override fun observeQuestions(): Flow<List<Question>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeQuestion(id: Long): Flow<Question?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun questionsForLesson(lessonId: Long) = dao.forLesson(lessonId).map { it.toDomain() }
    override suspend fun placementQuestions() = dao.placement(QuizScoring.PLACEMENT_QUESTION_COUNT).map { it.toDomain() }

    private fun validate(input: QuestionInput): QuestionInput {
        val errors = QuestionValidator.errors(input)
        if (errors.isNotEmpty()) throw InvalidQuestion(errors)
        return input.normalized()
    }

    private fun QuestionInput.options() =
        options.mapIndexed { i, o -> QuestionOptionEntity(questionId = 0, content = o.content, isCorrect = o.isCorrect, orderIndex = i) }

    override suspend fun create(input: QuestionInput): Long {
        val value = validate(input)
        return dao.insertWithOptions(QuestionEntity(topicId = value.topicId, lessonId = value.lessonId, isPlacement = value.isPlacement,
            answerType = value.answerType, content = value.content, explanation = value.explanation.ifEmpty { null },
            createdAt = clock.millis()), value.options())
    }

    override suspend fun update(id: Long, input: QuestionInput) {
        val value = validate(input)
        val current = dao.get(id)?.question ?: throw QuestionMissing()
        if (dao.isAnswered(id)) throw QuestionInUse()
        val updated = current.copy(topicId = value.topicId, lessonId = value.lessonId, isPlacement = value.isPlacement,
            answerType = value.answerType, content = value.content, explanation = value.explanation.ifEmpty { null })
        // Restricted FKs on attempt_answers back up the check above.
        val count = try { dao.replaceWithOptions(updated, value.options()) } catch (_: SQLiteConstraintException) { throw QuestionInUse() }
        if (count == 0) throw QuestionMissing()
    }

    override suspend fun delete(id: Long) {
        if (dao.isAnswered(id)) throw QuestionInUse()
        val count = try { dao.delete(id) } catch (_: SQLiteConstraintException) { throw QuestionInUse() }
        if (count == 0) throw QuestionMissing()
    }
}
