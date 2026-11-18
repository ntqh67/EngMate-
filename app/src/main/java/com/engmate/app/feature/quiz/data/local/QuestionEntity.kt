package com.engmate.app.feature.quiz.data.local

import androidx.room.*
import com.engmate.app.feature.vocabulary.data.local.TopicEntity
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.core.database.AnswerType

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = GrammarLessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["topic_id"]), Index(value = ["lesson_id"]), Index(value = ["is_placement"])]
)
data class QuestionEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    @ColumnInfo(name = "lesson_id") val lessonId: Long? = null,
    @ColumnInfo(name = "is_placement", defaultValue = "0") val isPlacement: Boolean = false,
    @ColumnInfo(name = "answer_type") val answerType: AnswerType = AnswerType.SINGLE_CHOICE,
    val content: String,
    val explanation: String? = null,
    @ColumnInfo(name = "correct_answer") val correctAnswer: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
)
