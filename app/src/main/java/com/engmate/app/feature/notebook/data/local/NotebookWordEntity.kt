package com.engmate.app.feature.notebook.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.local.WordEntity

@Entity(
    tableName = "notebook_words",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_uid"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["user_uid", "word_id"], unique = true), Index(value = ["word_id"])]
)
data class NotebookWordEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_uid") val userUid: String,
    @ColumnInfo(name = "word_id") val wordId: Long,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
)
