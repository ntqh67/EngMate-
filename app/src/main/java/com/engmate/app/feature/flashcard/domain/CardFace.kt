package com.engmate.app.feature.flashcard.domain

import com.engmate.app.feature.notebook.domain.WordExample
import com.engmate.app.feature.notebook.domain.WordNote
import com.engmate.app.feature.vocabulary.domain.Word

/** What the learner sees on the front of a card: clues only, the word itself is hidden. */
data class CardFront(val definition: String, val example: WordExample?, val collocations: List<String>)

object CardFace {
    /** The user's own meaning wins, then the Vietnamese meaning, then the English definition. Latest own example wins. */
    fun front(word: Word, note: WordNote?): CardFront {
        val definition = note?.customMeaning ?: word.meaningVi ?: word.meaningEn
        val example = note?.examples?.lastOrNull()
            ?: word.exampleSentence?.let { WordExample(it, word.exampleMeaning.orEmpty()) }
        val collocations = word.collocations + note?.collocations.orEmpty()
        return CardFront(mask(definition, word.word), example?.let { WordExample(mask(it.en, word.word), it.vi) },
            collocations.map { mask(it, word.word) })
    }

    /**
     * Hides the answer, including inflected forms: "looking up" for "look up", "appreciated" for "appreciate".
     * shortcut: matches each word's stem (length - 2 for words over 4 letters) plus trailing letters, so a very
     * short stem can also hide an unrelated word; switch to proper inflection rules if that shows up in practice.
     */
    fun mask(text: String, word: String): String {
        val pattern = word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString("\\s+") { part -> Regex.escape(if (part.length > 4) part.dropLast(2) else part) + "[\\p{L}]*" }
        if (pattern.isEmpty()) return text
        return text.replace(Regex(pattern, RegexOption.IGNORE_CASE), "_____")
    }
}
