package com.engmate.app.feature.flashcard

import com.engmate.app.feature.flashcard.domain.CardFace
import com.engmate.app.feature.notebook.domain.WordExample
import com.engmate.app.feature.notebook.domain.WordNote
import com.engmate.app.feature.vocabulary.domain.Word
import org.junit.Assert.*
import org.junit.Test

class CardFaceTest {
    private fun word(text: String, meaningEn: String = "def", vi: String? = null, example: String? = null, collocations: List<String> = emptyList()) =
        Word(1, null, text, null, null, meaningEn, vi, example, null, null, collocations, emptyList(), true)

    @Test fun maskHidesInflectedFormsAndPhrases() {
        assertEquals("Things started _____ after Jim moved", CardFace.mask("Things started looking up after Jim moved", "look up"))
        assertEquals("I really _____ your help", CardFace.mask("I really appreciate your help", "appreciate"))
        assertEquals("_____? Is anyone there?", CardFace.mask("Hello? Is anyone there?", "hello"))
        assertEquals("deeply _____", CardFace.mask("deeply appreciated", "appreciate"))
        assertEquals("No match here.", CardFace.mask("No match here.", "nuance"))
    }

    @Test fun frontPrefersOwnMeaningAndLatestOwnExample() {
        val w = word("appreciate", "To be grateful for.", vi = "trân trọng", example = "I appreciate it.", collocations = listOf("deeply appreciate"))
        val plain = CardFace.front(w, null)
        assertEquals("trân trọng", plain.definition)
        assertEquals("I _____ it.", plain.example!!.en)
        assertEquals(listOf("deeply _____"), plain.collocations)

        val note = WordNote("appreciate", "biết ơn", listOf(WordExample("Old.", ""), WordExample("Thanks, I appreciate that!", "Cảm ơn!")),
            listOf("truly appreciate"), emptyList(), null, null)
        val mine = CardFace.front(w, note)
        assertEquals("biết ơn", mine.definition)
        assertEquals(WordExample("Thanks, I _____ that!", "Cảm ơn!"), mine.example)
        assertEquals(listOf("deeply _____", "truly _____"), mine.collocations)
    }

    @Test fun englishDefinitionHidesTheWordToo() {
        val front = CardFace.front(word("hello", "\"Hello!\" or an equivalent greeting."), null)
        assertEquals("\"_____!\" or an equivalent greeting.", front.definition)
        assertNull(front.example)
    }
}
