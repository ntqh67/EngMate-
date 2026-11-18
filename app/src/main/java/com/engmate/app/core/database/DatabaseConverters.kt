package com.engmate.app.core.database

import androidx.room.TypeConverter
import org.json.JSONArray

enum class WordLearningStatus { NEW, LEARNING, REVIEW, MASTERED }
enum class AnswerType { SINGLE_CHOICE, TEXT }
enum class ActivityType { VOCABULARY, FLASHCARD, GRAMMAR, QUIZ, PLACEMENT }

class DatabaseConverters {
    @TypeConverter fun encodeStrings(values: List<String>): String = JSONArray(values).toString()
    @TypeConverter fun decodeStrings(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val value = array.get(index)
            require(value is String) { "Expected a JSON string array" }
            value
        }
    }
    @TypeConverter fun encodeStatus(value: WordLearningStatus): String = value.name
    @TypeConverter fun decodeStatus(value: String): WordLearningStatus = WordLearningStatus.valueOf(value)
    @TypeConverter fun encodeAnswerType(value: AnswerType): String = value.name
    @TypeConverter fun decodeAnswerType(value: String): AnswerType = when (value) {
        "MULTIPLE_CHOICE" -> AnswerType.SINGLE_CHOICE
        "FILL_BLANK" -> AnswerType.TEXT
        else -> AnswerType.valueOf(value)
    }
    @TypeConverter fun encodeActivityType(value: ActivityType): String = value.name
    @TypeConverter fun decodeActivityType(value: String): ActivityType = ActivityType.valueOf(value)
}
