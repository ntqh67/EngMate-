package com.engmate.app.feature.notebook.data.local

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.local.WordEntity

@Entity(
    tableName = "word_notes",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["uid"], childColumns = ["user_uid"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["user_uid", "word_id"], unique = true), Index(value = ["word_id"])]
)
data class WordNoteEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_uid") val userUid: String,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "custom_meaning") val customMeaning: String? = null,
    @ColumnInfo(name = "custom_examples") val customExamples: List<String> = emptyList(),
    @ColumnInfo(name = "custom_collocations") val customCollocations: List<String> = emptyList(),
    @ColumnInfo(name = "custom_idioms") val customIdioms: List<String> = emptyList(),
    @ColumnInfo(name = "personal_note") val personalNote: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
)
