package com.engmate.app.feature.flashcard.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.engmate.app.core.database.WordLearningStatus

@Entity(
    tableName = "word_progress",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_uid"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["user_uid", "word_id"], unique = true), Index(value = ["word_id"]), Index(value = ["user_uid", "next_review_at"])]
)
data class WordProgressEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_uid") val userUid: String,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(defaultValue = "0") val repetition: Int = 0,
    @ColumnInfo(name = "interval_days", defaultValue = "1") val intervalDays: Int = 1,
    @ColumnInfo(name = "ease_factor", defaultValue = "2.5") val easeFactor: Double = 2.5,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long? = null,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,
    val status: WordLearningStatus = WordLearningStatus.NEW,
)
