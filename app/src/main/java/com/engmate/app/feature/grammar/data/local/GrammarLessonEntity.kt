package com.engmate.app.feature.grammar.data.local

import androidx.room.*

@Entity(
    tableName = "grammar_lessons",
    indices = [Index(value = ["level", "order_index"])]
)
data class GrammarLessonEntity(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val level: String,
    val summary: String? = null,
    val content: String,
    val examples: List<String> = emptyList(),
    @ColumnInfo(name = "order_index", defaultValue = "0") val orderIndex: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
)
