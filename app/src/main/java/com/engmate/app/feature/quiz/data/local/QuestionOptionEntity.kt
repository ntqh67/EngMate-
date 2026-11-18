package com.engmate.app.feature.quiz.data.local

import androidx.room.*

@Entity(
    tableName = "question_options",
    foreignKeys = [
        ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["question_id"]), Index(value = ["id", "question_id"], unique = true)]
)
data class QuestionOptionEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    val content: String,
    @ColumnInfo(name = "is_correct", defaultValue = "0") val isCorrect: Boolean = false,
    @ColumnInfo(name = "order_index", defaultValue = "0") val orderIndex: Int = 0,
)
