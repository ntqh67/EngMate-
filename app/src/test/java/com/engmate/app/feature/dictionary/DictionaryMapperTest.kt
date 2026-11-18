package com.engmate.app.feature.dictionary

import com.engmate.app.feature.dictionary.data.remote.EntryDto
import com.engmate.app.feature.dictionary.data.remote.MeaningDto
import com.engmate.app.feature.dictionary.data.remote.toCachedWord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/** Uses real dictionaryapi.dev responses saved in src/test/resources/dictionary. */
class DictionaryMapperTest {
    private fun fixture(name: String): List<EntryDto> = Gson().fromJson(
        javaClass.classLoader!!.getResource("dictionary/$name.json")!!.readText(), object : TypeToken<List<EntryDto>>() {}.type)

    @Test fun helloUsesFirstIpaWithTextAndFirstExample() {
        val word = fixture("hello").toCachedWord()!!
        assertEquals("hello", word.word)
        assertEquals("/həˈləʊ/", word.phonetic) // phonetics[0] has audio but no text
        assertEquals("https://api.dictionaryapi.dev/media/pronunciations/en/hello-au.mp3", word.audioUrl)
        assertEquals("noun", word.partOfSpeech)
        assertEquals("\"Hello!\" or an equivalent greeting.", word.meaningEn)
        assertEquals("Hello, everyone.", word.exampleSentence) // first definition that has an example
    }

    @Test fun bankMergesFourEntries() {
        val entries = fixture("bank")
        assertEquals(4, entries.size)
        val word = entries.toCachedWord()!!
        assertEquals("/bæŋk/", word.phonetic)
        assertTrue(word.meaningEn.startsWith("An institution where one can place and borrow money"))
    }

    @Test fun missingIpaAndExamplesStayNull() {
        val word = fixture("resilient").toCachedWord()!!
        assertNull(word.phonetic)
        assertNull(word.exampleSentence)
        assertEquals("adjective", word.partOfSpeech)
        assertNotNull(word.audioUrl)
    }

    @Test fun phrasesWork() {
        val word = fixture("look_up").toCachedWord()!!
        assertEquals("look up", word.word)
        assertEquals("verb", word.partOfSpeech)
        assertEquals("To have better prospects.", word.meaningEn)
    }

    @Test fun nothingUsableGivesNull() {
        assertNull(emptyList<EntryDto>().toCachedWord())
        assertNull(listOf(EntryDto("x", meanings = listOf(MeaningDto("noun", definitions = emptyList())))).toCachedWord())
    }

    @Test fun protocolRelativeAudioGetsHttps() {
        val entries: List<EntryDto> = Gson().fromJson("""[{"word":"hi","phonetics":[{"text":"/haɪ/","audio":"//ssl.gstatic.com/hi.mp3"}],
            "meanings":[{"partOfSpeech":"exclamation","definitions":[{"definition":"Hello."}]}]}]""", object : TypeToken<List<EntryDto>>() {}.type)
        val entry = entries.toCachedWord()!!
        assertEquals("https://ssl.gstatic.com/hi.mp3", entry.audioUrl)
    }
}
