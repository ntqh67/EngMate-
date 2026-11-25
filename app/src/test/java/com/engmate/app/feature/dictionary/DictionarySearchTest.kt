package com.engmate.app.feature.dictionary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.dictionary.data.ApiDictionaryRepository
import com.engmate.app.feature.dictionary.data.remote.DictionaryApi
import com.engmate.app.feature.dictionary.data.remote.EntryDto
import com.engmate.app.feature.dictionary.domain.DictionaryRepository
import com.engmate.app.feature.dictionary.domain.LookupResult
import com.engmate.app.feature.home.presentation.searchFlow
import com.engmate.app.feature.vocabulary.data.RoomWordRepository
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/** ApiDictionaryRepository and the Home search flow against a fake API and an in-memory database. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class DictionarySearchTest {
    private lateinit var db: EngMateDatabase
    private lateinit var words: RoomWordRepository

    /** Serves saved real responses; anything else is a 404. [failure] simulates no network. */
    private class FakeApi(var failure: Exception? = null, var status: Int? = null) : DictionaryApi {
        val calls = mutableListOf<String>()
        override suspend fun entries(word: String): Response<List<EntryDto>> {
            calls += word
            failure?.let { throw it }
            status?.let { return Response.error(it, "".toResponseBody(null)) }
            val file = javaClass.classLoader!!.getResource("dictionary/${word.replace(' ', '_')}.json")
                ?: return Response.error(404, """{"title":"No Definitions Found"}""".toResponseBody(null))
            return Response.success(Gson().fromJson(file.readText(), object : TypeToken<List<EntryDto>>() {}.type))
        }
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EngMateDatabase::class.java)
            .allowMainThreadQueries().build()
        words = RoomWordRepository(db.vocabulary(), Clock.systemUTC())
    }
    @After fun close() = db.close()

    @Test fun savedWordIsCachedAndNextLookupStaysOffline() = runBlocking<Unit> {
        val api = FakeApi()
        val dictionary = ApiDictionaryRepository(api, words)
        val first = dictionary.lookup("  Hello ", save = true) as LookupResult.Found
        assertEquals("hello", first.word.word)
        assertTrue(first.word.isCached)
        assertEquals(listOf("hello"), api.calls)

        api.failure = IOException("offline")
        val second = dictionary.lookup("HELLO") as LookupResult.Found
        assertEquals(first.word.id, second.word.id)
        assertEquals("cached word needs no network", 1, api.calls.size)
    }

    @Test fun lookupWithoutSavingShowsTheWordButStoresNothing() = runBlocking<Unit> {
        val api = FakeApi()
        val dictionary = ApiDictionaryRepository(api, words)
        val hit = dictionary.lookup("hello") as LookupResult.Found
        assertEquals("hello", hit.word.word)
        assertEquals("not saved, so no database id", 0L, hit.word.id)
        assertFalse(hit.word.isCached)
        assertTrue("nothing written to the device", words.search("hello").isEmpty())

        dictionary.lookup("hello")
        assertEquals("an unsaved word is fetched again", 2, api.calls.size)
    }

    @Test fun singleLetterNeverGoesOnline() = runBlocking<Unit> {
        val api = FakeApi()
        assertEquals(LookupResult.NotFound, ApiDictionaryRepository(api, words).lookup("a"))
        assertTrue(api.calls.isEmpty())
    }

    @Test fun notFoundOfflineAndServerErrorsAreReported() = runBlocking<Unit> {
        val api = FakeApi()
        val dictionary = ApiDictionaryRepository(api, words)
        assertEquals(LookupResult.NotFound, dictionary.lookup("helo"))
        api.failure = IOException("no network")
        assertEquals(LookupResult.Offline, dictionary.lookup("nuance"))
        api.failure = null; api.status = 500
        assertEquals(LookupResult.Failed, dictionary.lookup("nuance"))
        assertTrue("nothing was cached", words.search("nuance").isEmpty() && words.search("helo").isEmpty())
    }

    @Test fun inputThatIsNotAWordNeverGoesOnline() = runBlocking<Unit> {
        val api = FakeApi()
        val dictionary = ApiDictionaryRepository(api, words)
        listOf("%", "chăm chỉ", "123", "a".repeat(60)).forEach { assertEquals(it, LookupResult.NotFound, dictionary.lookup(it)) }
        assertTrue(api.calls.isEmpty())
    }

    @Test fun searchShowsLocalFirstThenPutsTheDictionaryHitOnTopWithoutSavingIt() = runBlocking<Unit> {
        db.vocabulary().insertWord(WordEntity(word = "hello world", meaningEn = "a first program"))
        val api = FakeApi()
        val dictionary = ApiDictionaryRepository(api, words)
        val states = searchFlow("hello", words, dictionary).toList()
        assertEquals(2, states.size)
        assertTrue("local results while the lookup runs", states[0].searching)
        assertEquals(listOf("hello world"), states[0].results.map { it.word })
        assertEquals(listOf("hello", "hello world"), states[1].results.map { it.word })
        assertFalse(states[1].searching)
        assertEquals(DictionaryRepository.UNSAVED_ID, states[1].results.first().id)
        assertNull("searching must not save the word", db.vocabulary().findWord("hello"))

        val again = searchFlow("hello", words, dictionary).toList()
        assertEquals("previewed word is reused from memory", 1, api.calls.size)
        assertEquals(listOf("hello", "hello world"), again.last().results.map { it.word })
        assertNull(db.vocabulary().findWord("hello"))
    }

    @Test fun openingAPreviewedWordSavesItWithoutAnotherCall() = runBlocking<Unit> {
        val api = FakeApi()
        val dictionary = ApiDictionaryRepository(api, words)
        assertTrue(dictionary.preview("hello") is LookupResult.Found)
        val opened = dictionary.lookup("hello") as LookupResult.Found
        assertNotEquals(DictionaryRepository.UNSAVED_ID, opened.word.id)
        assertTrue(opened.word.isCached)
        assertNotNull(db.vocabulary().findWord("hello"))
        assertEquals(listOf("hello"), api.calls)
    }

    @Test fun wordUnknownToTheDictionaryIsNotShownOrSaved() = runBlocking<Unit> {
        val api = FakeApi()
        val last = searchFlow("helo", words, ApiDictionaryRepository(api, words)).toList().last()
        assertTrue(last.results.isEmpty())
        assertNull(db.vocabulary().findWord("helo"))
        assertTrue("the search shows the hit but does not save it", words.search("hello").none { it.word == "hello" })

        dictionary.lookup("hello", save = true)   // what opening the word does
        val again = searchFlow("hello", words, dictionary).toList()
        assertEquals("saved word is an exact local match: no further call", 2, api.calls.size)
        assertEquals(1, again.size)
    }

    @Test fun searchExplainsNotFoundAndOffline() = runBlocking<Unit> {
        val api = FakeApi()
        assertEquals("Không tìm thấy “helo”.", searchFlow("helo", words, ApiDictionaryRepository(api, words)).toList().last().notice)
        api.failure = IOException("offline")
        assertTrue(searchFlow("nuance", words, ApiDictionaryRepository(api, words)).toList().last().notice!!.startsWith("Không có kết nối Internet"))
        assertEquals(listOf(SearchStateCheck.EMPTY), searchFlow("  ", words, ApiDictionaryRepository(api, words)).toList().map { SearchStateCheck.of(it.results.size) })
    }

    private enum class SearchStateCheck { EMPTY, SOME; companion object { fun of(n: Int) = if (n == 0) EMPTY else SOME } }
}
