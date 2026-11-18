package com.engmate.app.feature.quiz.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.local.TopicEntity
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity

@Entity(
    tableName = "quiz_attempts",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_uid"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = GrammarLessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["user_uid", "completed_at"]), Index(value = ["topic_id"]), Index(value = ["lesson_id"])]
)
data class QuizAttemptEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_uid") val userUid: String,
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    @ColumnInfo(name = "lesson_id") val lessonId: Long? = null,
    @ColumnInfo(name = "is_placement", defaultValue = "0") val isPlacement: Boolean = false,
    @ColumnInfo(name = "correct_count", defaultValue = "0") val correctCount: Int = 0,
    @ColumnInfo(name = "total_questions") val totalQuestions: Int,
    @ColumnInfo(name = "xp_earned", defaultValue = "0") val xpEarned: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
)
