package com.engmate.app.feature.grammar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "grammar_lessons")
data class GrammarLessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** A1 | A2 | B1 | B2 */
    val level: String,
    val summary: String?,
    val content: String,
    /** JSON array: [{"en":"…","vi":"…"}] */
    val examples: List<GrammarExample> = emptyList(),
    @ColumnInfo(name = "order_index", defaultValue = "0") val orderIndex: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
) {
    fun toDomain() = GrammarLesson(id, title, level, summary, content, examples, orderIndex, updatedAt)
}

class GrammarConverters {
    private val gson = Gson()
    private val type = object : TypeToken<List<GrammarExample>>() {}.type

    @TypeConverter fun examplesToJson(value: List<GrammarExample>): String = gson.toJson(value)

    @TypeConverter fun jsonToExamples(value: String?): List<GrammarExample> =
        if (value.isNullOrBlank()) emptyList() else gson.fromJson<List<GrammarExample>>(value, type).orEmpty()
}
