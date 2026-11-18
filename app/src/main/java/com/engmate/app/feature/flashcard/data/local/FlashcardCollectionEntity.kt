package com.engmate.app.feature.flashcard.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity

@Entity(
    tableName = "flashcard_collections",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["user_id"])]
)
data class FlashcardCollectionEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userUid: String,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
)
