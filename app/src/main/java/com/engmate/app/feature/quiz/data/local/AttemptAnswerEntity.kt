package com.engmate.app.feature.quiz.data.local

import androidx.room.*

@Entity(
    tableName = "attempt_answers",
    foreignKeys = [
        ForeignKey(entity = QuizAttemptEntity::class, parentColumns = ["id"], childColumns = ["attempt_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = QuestionOptionEntity::class, parentColumns = ["id", "question_id"], childColumns = ["selected_option_id", "question_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["attempt_id", "question_id"], unique = true), Index(value = ["question_id"]), Index(value = ["selected_option_id", "question_id"])]
)
data class AttemptAnswerEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "attempt_id") val attemptId: Long,
    @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "selected_option_id") val selectedOptionId: Long? = null,
    @ColumnInfo(name = "answer_text") val answerText: String? = null,
    @ColumnInfo(name = "question_snapshot") val questionSnapshot: String = "",
    @ColumnInfo(name = "selected_option_snapshot") val selectedOptionSnapshot: String? = null,
    @ColumnInfo(name = "explanation_snapshot") val explanationSnapshot: String? = null,
    @ColumnInfo(name = "is_correct", defaultValue = "0") val isCorrect: Boolean = false,
)
