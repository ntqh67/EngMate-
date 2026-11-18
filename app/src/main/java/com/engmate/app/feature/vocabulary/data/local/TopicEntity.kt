package com.engmate.app.feature.vocabulary.data.local

import androidx.room.*

@Entity(
    tableName = "topics"
)
data class TopicEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val category: String? = null,
)
