package com.engmate.app

import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.grammar.domain.LessonInput
import com.engmate.app.feature.grammar.domain.LessonValidator
import com.engmate.app.feature.quiz.domain.AnswerType
import com.engmate.app.feature.quiz.domain.OptionInput
import com.engmate.app.feature.quiz.domain.QuestionInput
import com.engmate.app.feature.quiz.domain.QuestionValidator
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled JSON must pass the same validators the app applies to Firestore content, or it would be silently dropped. */
class SeedDataTest {
    private fun asset(name: String) = JsonParser.parseString(File("src/main/assets/$name").readText(Charsets.UTF_8)).asJsonObject

    @Test fun grammarSeedPassesValidators() {
        val lessons = asset("grammar_seed.json").getAsJsonArray("lessons")
        assertTrue(lessons.size() >= 14)
        val ids = HashSet<Long>()
        for (el in lessons) {
            val l = el.asJsonObject
            assertTrue("duplicate lesson id", ids.add(l["id"].asLong))
            val examples = l.getAsJsonArray("examples").map { GrammarExample(it.asJsonObject["en"].asString, it.asJsonObject["vi"].asString) }
            val errors = LessonValidator.errors(LessonInput(l["title"].asString, l["level"].asString, l["summary"].asString, l["content"].asString, examples))
            assertTrue("${l["title"]}: $errors", errors.isEmpty())
            for (qe in l.getAsJsonArray("questions")) {
                val q = qe.asJsonObject
                val multiple = q["type"].asString == "MC"
                val options = if (multiple) q.getAsJsonArray("opts").mapIndexed { i, o -> OptionInput(o.asString, i == q["correct"].asInt) }
                else q.getAsJsonArray("accept").map { OptionInput(it.asString, true) }
                val input = QuestionInput(lessonId = l["id"].asLong, answerType = if (multiple) AnswerType.MULTIPLE_CHOICE else AnswerType.FILL_BLANK,
                    content = q["q"].asString, explanation = q["exp"].asString, options = options)
                val qErrors = QuestionValidator.errors(input)
                assertTrue("${l["title"]} / ${q["q"]}: $qErrors", qErrors.isEmpty())
            }
        }
    }

    @Test fun vocabularySeedIsComplete() {
        val topics = asset("vocabulary_seed.json").getAsJsonArray("topics")
        val words = HashSet<String>()
        for (t in topics) for (w in t.asJsonObject.getAsJsonArray("words")) {
            val o = w.asJsonObject
            assertTrue("duplicate ${o["word"]}", words.add(o["word"].asString.lowercase()))
            for (f in listOf("word", "phonetic", "vi", "en", "example")) assertTrue("${o["word"]} missing $f", o[f].asString.isNotBlank())
        }
        assertTrue("only ${words.size} words", words.size >= 400)
        assertEquals(15, topics.size())
    }
}
