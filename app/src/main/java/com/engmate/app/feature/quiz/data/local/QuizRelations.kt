package com.engmate.app.feature.quiz.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.quiz.domain.*

fun QuestionWithOptions.toDomain() = Question(
    question.id, question.topicId, question.lessonId, question.isPlacement,
    when (question.answerType) {
        com.engmate.app.core.database.AnswerType.SINGLE_CHOICE -> AnswerType.MULTIPLE_CHOICE
        com.engmate.app.core.database.AnswerType.TEXT -> AnswerType.FILL_BLANK
    },
    question.content, question.explanation,
    options.sortedWith(compareBy({ it.orderIndex }, { it.id }))
        .map { QuestionOption(it.id, it.content, it.isCorrect, it.orderIndex) },
    question.createdAt,
)

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

data class AttemptDetailWithAnswers(
    @Embedded val attempt: QuizAttemptEntity,
    @ColumnInfo(name = "lesson_title") val lessonTitle: String?,
    @Relation(entity = AttemptAnswerEntity::class, parentColumn = "id", entityColumn = "attempt_id")
    val answers: List<AnswerWithQuestion>,
)

data class AttemptRow(
    @Embedded val attempt: QuizAttemptEntity,
    @ColumnInfo(name = "lesson_title") val lessonTitle: String?,
)

/** [topicTitles] comes from TopicLookup; a topic without a name falls back to "Chủ đề #id". */
internal fun attemptTitle(attempt: QuizAttemptEntity, lessonTitle: String?, topicTitles: Map<Long, String> = emptyMap()): String = when {
    attempt.isPlacement -> PLACEMENT_TITLE
    lessonTitle != null -> lessonTitle
    attempt.topicId != null -> topicTitles[attempt.topicId] ?: "Chủ đề #${attempt.topicId}"
    else -> "Bài làm"
}

fun AttemptRow.toSummary(topicTitles: Map<Long, String> = emptyMap()) = AttemptSummary(
    attempt.id, attemptTitle(attempt, lessonTitle, topicTitles), attempt.isPlacement, attempt.correctCount,
    attempt.totalQuestions, attempt.xpEarned, attempt.completedAt, attempt.lessonId, attempt.topicId,
)

fun AttemptDetailWithAnswers.toDetail(topicTitles: Map<Long, String> = emptyMap()) = AttemptDetail(
    AttemptRow(attempt, lessonTitle).toSummary(topicTitles),
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
