package com.engmate.app.feature.progress.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.core.database.ActivityType

@Entity(
    tableName = "learning_activity",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_uid"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["user_uid", "study_date"]), Index(value = ["user_uid", "occurred_at"])]
)
data class LearningActivityEntity(
@PrimaryKey @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "user_uid") val userUid: String,
    @ColumnInfo(name = "activity_type") val activityType: ActivityType,
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    @ColumnInfo(name = "xp_earned", defaultValue = "0") val xpEarned: Int = 0,
    @ColumnInfo(name = "duration_seconds", defaultValue = "0") val durationSeconds: Int = 0,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "study_date") val studyDate: String,
    @ColumnInfo(name = "words_learned", defaultValue = "0") val wordsLearned: Int = 0,
    @ColumnInfo(name = "words_reviewed", defaultValue = "0") val wordsReviewed: Int = 0,
    @ColumnInfo(name = "quizzes_completed", defaultValue = "0") val quizzesCompleted: Int = 0,
)
