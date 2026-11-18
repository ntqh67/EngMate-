package com.engmate.app.feature.vocabulary.data.local

import androidx.room.*

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["topic_id"]), Index(value = ["word"], unique = true)]
)
data class WordEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    val phonetic: String? = null,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String? = null,
    @ColumnInfo(name = "meaning_en") val meaningEn: String,
    @ColumnInfo(name = "meaning_vi") val meaningVi: String? = null,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String? = null,
    @ColumnInfo(name = "example_meaning") val exampleMeaning: String? = null,
    @ColumnInfo(name = "audio_url") val audioUrl: String? = null,
    val collocations: List<String> = emptyList(),
    val idioms: List<String> = emptyList(),
    @ColumnInfo(name = "is_cached", defaultValue = "0") val isCached: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
)
