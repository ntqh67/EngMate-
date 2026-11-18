package com.engmate.app.feature.progress.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity

@Entity(
    tableName = "daily_study_stat",
    primaryKeys = ["user_uid", "study_date"],
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_uid"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class DailyStudyStatEntity(
@ColumnInfo(name = "user_uid") val userUid: String,
    @ColumnInfo(name = "study_date") val studyDate: String,
    @ColumnInfo(name = "xp_earned", defaultValue = "0") val xpEarned: Long = 0,
    @ColumnInfo(name = "study_seconds", defaultValue = "0") val studySeconds: Long = 0,
    @ColumnInfo(name = "words_learned", defaultValue = "0") val wordsLearned: Long = 0,
    @ColumnInfo(name = "words_reviewed", defaultValue = "0") val wordsReviewed: Long = 0,
    @ColumnInfo(name = "quizzes_completed", defaultValue = "0") val quizzesCompleted: Long = 0,
)
