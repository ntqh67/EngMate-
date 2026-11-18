package com.engmate.app.feature.flashcard.data.local

import androidx.room.*

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(entity = FlashcardCollectionEntity::class, parentColumns = ["id"], childColumns = ["collection_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["collection_id"])]
)
data class FlashcardEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    val word: String,
    val meaning: String,
    val example: String? = null,
    val phonetic: String? = null,
)
