package com.engmate.app.feature.grammar.data.local

import androidx.room.TypeConverter
import com.engmate.app.feature.grammar.domain.GrammarExample
import org.json.JSONArray
import org.json.JSONObject

/** Reads both the original plain examples and the bilingual examples from main. */
class GrammarConverters {
    @TypeConverter fun examplesToJson(values: List<GrammarExample>): String =
        JSONArray(values.map { JSONObject().put("en", it.en).put("vi", it.vi) }).toString()

    @TypeConverter fun jsonToExamples(json: String?): List<GrammarExample> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { index ->
            when (val item = array.get(index)) {
                is String -> GrammarExample(item, "")
                is JSONObject -> GrammarExample(item.getString("en"), item.optString("vi", ""))
                else -> error("Invalid grammar example at $index")
            }
        }
    }
}
