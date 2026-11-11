package com.engmate.app.feature.quiz.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.quiz.domain.*

data class QuestionWithOptions(
    @Embedded val question: QuestionEntity,
    @Relation(parentColumn = "id", entityColumn = "question_id")
    val options: List<QuestionOptionEntity>,
) {
    fun toDomain() = Question(
        question.id, question.topicId, question.lessonId, question.isPlacement, question.answerType,
        question.content, question.explanation,
        options.sortedWith(compareBy({ it.orderIndex }, { it.id }))
            .map { QuestionOption(it.id, it.content, it.isCorrect, it.orderIndex) },
        question.createdAt,
    )
}

data class LessonWithQuestions(
    @Embedded val lesson: GrammarLessonEntity,
    @Relation(entity = QuestionEntity::class, parentColumn = "id", entityColumn = "lesson_id")
    val questions: List<QuestionWithOptions>,
)

/** One answer together with its question and that question's options. */
data class AnswerWithQuestion(
    @Embedded val answer: AttemptAnswerEntity,
    @Relation(entity = QuestionEntity::class, parentColumn = "question_id", entityColumn = "id")
    val question: QuestionWithOptions,
)

data class AttemptWithAnswers(
    @Embedded val attempt: QuizAttemptEntity,
    @ColumnInfo(name = "lesson_title") val lessonTitle: String?,
    @Relation(entity = AttemptAnswerEntity::class, parentColumn = "id", entityColumn = "attempt_id")
    val answers: List<AnswerWithQuestion>,
)

data class AttemptRow(
    @Embedded val attempt: QuizAttemptEntity,
    @ColumnInfo(name = "lesson_title") val lessonTitle: String?,
)

internal fun attemptTitle(attempt: QuizAttemptEntity, lessonTitle: String?): String = when {
    attempt.isPlacement -> PLACEMENT_TITLE
    lessonTitle != null -> lessonTitle
    // TODO(Khang): show the topic name once the topics table exists.
    attempt.topicId != null -> "Chủ đề #${attempt.topicId}"
    else -> "Bài làm"
}

fun AttemptRow.toSummary() = AttemptSummary(
    attempt.id, attemptTitle(attempt, lessonTitle), attempt.isPlacement, attempt.correctCount,
    attempt.totalQuestions, attempt.xpEarned, attempt.completedAt,
)

fun AttemptWithAnswers.toDetail() = AttemptDetail(
    AttemptRow(attempt, lessonTitle).toSummary(),
    answers.sortedBy { it.answer.id }.map { row ->
        val question = row.question.toDomain()
        AnsweredQuestion(
            questionId = question.id,
            question = question.content,
            answerType = question.answerType,
            given = when (question.answerType) {
                AnswerType.MULTIPLE_CHOICE -> question.options.firstOrNull { it.id == row.answer.selectedOptionId }?.content
                AnswerType.FILL_BLANK -> row.answer.answerText?.takeIf { it.isNotBlank() }
            },
            correctAnswers = question.correctOptions.map { it.content },
            explanation = question.explanation,
            isCorrect = row.answer.isCorrect,
        )
    },
)
