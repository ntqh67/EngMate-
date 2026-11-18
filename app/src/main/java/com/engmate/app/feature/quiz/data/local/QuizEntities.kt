package com.engmate.app.feature.quiz.data.local

import androidx.room.*
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.RESTRICT
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.quiz.domain.AnswerType

// TODO(Khang): add ForeignKey(topic_id -> topics.id) to questions and quiz_attempts once TopicEntity exists.

@Entity(
    tableName = "questions",
    foreignKeys = [ForeignKey(entity = GrammarLessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = RESTRICT)],
    indices = [Index("topic_id"), Index("lesson_id"), Index("is_placement")],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    @ColumnInfo(name = "lesson_id") val lessonId: Long? = null,
    @ColumnInfo(name = "is_placement", defaultValue = "0") val isPlacement: Boolean = false,
    @ColumnInfo(name = "answer_type") val answerType: AnswerType,
    val content: String,
    val explanation: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
)

@Entity(
    tableName = "question_options",
    foreignKeys = [ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = CASCADE)],
    indices = [Index("question_id")],
)
data class QuestionOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    val content: String,
    @ColumnInfo(name = "is_correct", defaultValue = "0") val isCorrect: Boolean = false,
    @ColumnInfo(name = "order_index", defaultValue = "0") val orderIndex: Int = 0,
)

@Entity(
    tableName = "quiz_attempts",
    foreignKeys = [
        // History belongs to the local profile: deleting the profile removes its attempts.
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_id"], onDelete = CASCADE),
        ForeignKey(entity = GrammarLessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = RESTRICT),
    ],
    indices = [Index("user_id"), Index("topic_id"), Index("lesson_id")],
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    @ColumnInfo(name = "lesson_id") val lessonId: Long? = null,
    @ColumnInfo(name = "is_placement", defaultValue = "0") val isPlacement: Boolean = false,
    @ColumnInfo(name = "correct_count", defaultValue = "0") val correctCount: Int = 0,
    @ColumnInfo(name = "total_questions") val totalQuestions: Int,
    @ColumnInfo(name = "xp_earned", defaultValue = "0") val xpEarned: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
)

@Entity(
    tableName = "attempt_answers",
    foreignKeys = [
        ForeignKey(entity = QuizAttemptEntity::class, parentColumns = ["id"], childColumns = ["attempt_id"], onDelete = CASCADE),
        ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = RESTRICT),
        ForeignKey(entity = QuestionOptionEntity::class, parentColumns = ["id"], childColumns = ["selected_option_id"], onDelete = RESTRICT),
    ],
    indices = [Index("attempt_id"), Index("question_id"), Index("selected_option_id")],
)
data class AttemptAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "attempt_id") val attemptId: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "selected_option_id") val selectedOptionId: Long? = null,
    @ColumnInfo(name = "answer_text") val answerText: String? = null,
    @ColumnInfo(name = "is_correct", defaultValue = "0") val isCorrect: Boolean = false,
)

class QuizConverters {
    @TypeConverter fun answerTypeToText(value: AnswerType): String = value.name
    @TypeConverter fun textToAnswerType(value: String): AnswerType = AnswerType.valueOf(value)
}
