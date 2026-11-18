package com.engmate.app.feature.notebook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.flashcard.data.RoomReviewRepository
import com.engmate.app.feature.flashcard.domain.Sm2
import com.engmate.app.feature.notebook.data.ExampleCodec
import com.engmate.app.feature.notebook.data.RoomHandbookRepository
import com.engmate.app.feature.notebook.data.RoomWordNoteRepository
import com.engmate.app.feature.notebook.domain.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.RoomWordRepository
import com.engmate.app.feature.vocabulary.data.local.TopicEntity
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.engmate.app.feature.vocabulary.domain.CachedWord
import com.engmate.app.feature.vocabulary.domain.SessionRequired
import com.engmate.app.feature.vocabulary.domain.WordMissing
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vocabulary, handbook, word note and review repositories on the shared v3 schema (Room in-memory). */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class VocabularyRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: EngMateDatabase
    private val session = object : UserSession {
        override val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
        override val isDemo = true
    }
    private var now = 5_000L
    private val clock = object : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(now)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }
    private lateinit var words: RoomWordRepository
    private lateinit var handbooks: RoomHandbookRepository
    private lateinit var notes: RoomWordNoteRepository
    private lateinit var reviews: RoomReviewRepository

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, EngMateDatabase::class.java).allowMainThreadQueries().build()
        db.profiles().insert(UserProfileEntity("alice", "alice@example.com", "An", null, "A2", 15, 1, 1))
        words = RoomWordRepository(db.vocabulary(), clock)
        handbooks = RoomHandbookRepository(db.flashcards(), db.vocabulary(), db.notebook(), session, clock)
        notes = RoomWordNoteRepository(db.notebook(), db.vocabulary(), session, clock)
        reviews = RoomReviewRepository(db.flashcards(), db.vocabulary(), session, clock)
    }
    @After fun close() = db.close()

    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun cache(word: String, meaning: String = "A definition.") =
        words.cache(CachedWord(word, meaning, phonetic = "/x/", partOfSpeech = "noun", exampleSentence = "An example."))
    private inline fun <reified T : Throwable> fails(crossinline block: suspend () -> Unit): T =
        assertThrows(T::class.java) { runBlocking { block() } }

    @Test fun cacheNormalizesWordAndKeepsSeedFields() = runBlocking<Unit> {
        val topic = db.vocabulary().insertTopic(TopicEntity(title = "Daily Conversation"))
        db.vocabulary().insertWord(WordEntity(topicId = topic, word = "appreciate", meaningEn = "seed", meaningVi = "trân trọng",
            collocations = listOf("deeply appreciate")))
        words.cache(CachedWord("  Appreciate ", "To be grateful.", phonetic = "/əˈpriː.si.eɪt/"))
        val word = words.observe("APPRECIATE").first()!!
        assertEquals("trân trọng", word.meaningVi)
        assertEquals("seed", word.meaningEn)
        assertEquals(listOf("deeply appreciate"), word.collocations)
        assertEquals("/əˈpriː.si.eɪt/", word.phonetic)
        assertTrue(word.isCached)
        assertEquals(1, words.observeTopics().first().single().wordCount)

        cache("Look   Up")
        assertNotNull(words.observe("look up").first())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { words.cache(CachedWord(" ", "x")) } }
    }

    @Test fun suggestMatchesPrefixShortestFirstAndTreatsWildcardsLiterally() = runBlocking<Unit> {
        listOf("appreciate", "apply", "approach", "bank").forEach { cache(it) }
        assertEquals(listOf("apply", "approach", "appreciate"), words.suggest("APP"))
        assertEquals(emptyList<String>(), words.suggest("%"))
        assertEquals(emptyList<String>(), words.suggest("_"))
        assertEquals(emptyList<String>(), words.suggest("  "))
    }

    @Test fun handbookTitleRules() = runBlocking<Unit> {
        val id = handbooks.create("alice", "  Daily Conversation ")
        assertEquals("Daily Conversation", handbooks.observeHandbooks("alice").first().single().title)
        assertEquals("Nhập tên sổ tay", fails<InvalidHandbook> { handbooks.create("alice", "  ") }.message)
        assertEquals("Tên tối đa 40 ký tự", fails<InvalidHandbook> { handbooks.create("alice", "x".repeat(41)) }.message)
        assertEquals("Tên sổ tay đã tồn tại", fails<InvalidHandbook> { handbooks.create("alice", "daily CONVERSATION") }.message)
        handbooks.create("alice", "Từ vựng phỏng vấn")
        assertEquals("Tên sổ tay đã tồn tại", fails<InvalidHandbook> { handbooks.create("alice", "TỪ VỰNG PHỎNG VẤN") }.message)

        handbooks.rename("alice", id, "daily conversation") // same handbook, different case: allowed
        assertEquals("Tên sổ tay đã tồn tại", fails<InvalidHandbook> { handbooks.rename("alice", id, "Từ vựng phỏng vấn") }.message)
        fails<HandbookMissing> { handbooks.rename("alice", 999, "New") }
    }

    @Test fun handbookWordsCountsAndDeleteKeepsWords() = runBlocking<Unit> {
        listOf("hello", "bank").forEach { cache(it) }
        val daily = handbooks.create("alice", "Daily")
        now += 1
        val work = handbooks.create("alice", "Work")
        handbooks.addWord("alice", daily, "Hello"); handbooks.addWord("alice", daily, "HELLO") // second add is ignored
        handbooks.addWord("alice", daily, "bank"); handbooks.addWord("alice", work, "bank")
        fails<WordMissing> { handbooks.addWord("alice", daily, "never-cached") }
        fails<HandbookMissing> { handbooks.addWord("alice", 999, "bank") }

        assertEquals(setOf(daily, work), handbooks.observeHandbooksContaining("alice", "BANK").first())
        reviews.review("alice", "bank", Sm2.KNOWN)
        notes.setMeaning("alice", "hello", "xin chào") // after the card was added: the entry still shows it live
        val list = handbooks.observeHandbooks("alice").first()
        assertEquals("newest first", listOf(work, daily), list.map { it.id })
        assertEquals(listOf(1 to 1, 2 to 1), list.map { it.wordCount to it.knownCount })
        val entries = handbooks.observeEntries("alice", daily).first()
        assertEquals(listOf("hello", "bank"), entries.map { it.word })
        assertEquals("A definition.", entries[0].cardMeaning)
        assertEquals("xin chào", entries[0].displayMeaning); assertEquals(WordLearningStatus.NEW, entries[0].status)
        assertEquals("A definition.", entries[1].displayMeaning); assertEquals(WordLearningStatus.MASTERED, entries[1].status)

        handbooks.removeWord("alice", daily, "Hello")
        handbooks.delete("alice", work)
        assertEquals(listOf(daily), handbooks.observeHandbooks("alice").first().map { it.id })
        assertEquals(1, count("flashcards"))
        assertEquals("words, notes and progress stay", listOf(2, 1, 1), listOf(count("words"), count("word_notes"), count("word_progress")))
        fails<HandbookMissing> { handbooks.delete("alice", work) }
    }

    @Test fun wordNoteEditsOneFieldAtATime() = runBlocking<Unit> {
        db.vocabulary().insertWord(WordEntity(word = "appreciate", meaningEn = "seed", collocations = listOf("deeply appreciate")))
        notes.addExample("alice", "appreciate", " Thanks, I appreciate it! ", "Cảm ơn!")
        notes.addExample("alice", "Appreciate", "I appreciate your help.")
        notes.addCollocation("alice", "appreciate", "truly   appreciate")
        notes.setNote("alice", "appreciate", "Hay dùng trong email.")
        var note = notes.observe("alice", "appreciate").first()!!
        assertEquals(listOf(WordExample("Thanks, I appreciate it!", "Cảm ơn!"), WordExample("I appreciate your help.", "")), note.examples)
        assertEquals(listOf("truly appreciate"), note.collocations)
        assertNull(note.customMeaning)

        assertEquals("Nhập câu ví dụ tiếng Anh", fails<InvalidWordNote> { notes.addExample("alice", "appreciate", " ", "dịch") }.message)
        assertEquals("Cụm từ này đã có", fails<InvalidWordNote> { notes.addCollocation("alice", "appreciate", "Deeply Appreciate") }.message)
        assertEquals("Cụm từ này đã có", fails<InvalidWordNote> { notes.addCollocation("alice", "appreciate", "TRULY appreciate") }.message)
        fails<InvalidWordNote> { notes.setNote("alice", "appreciate", "x".repeat(301)) }
        fails<InvalidWordNote> { notes.setMeaning("alice", "appreciate", "x".repeat(121)) }

        notes.removeExample("alice", "appreciate", 0)
        notes.removeCollocation("alice", "appreciate", 0)
        notes.setMeaning("alice", "appreciate", "trân trọng")
        notes.setMeaning("alice", "appreciate", "  ") // blank brings back the dictionary meaning
        note = notes.observe("alice", "appreciate").first()!!
        assertEquals(listOf("I appreciate your help."), note.examples.map { it.en })
        assertTrue(note.collocations.isEmpty()); assertNull(note.customMeaning)
        assertEquals("Hay dùng trong email.", note.personalNote)
        assertEquals(1, count("word_notes"))
        fails<WordMissing> { notes.setNote("alice", "never-cached", "x") }
    }

    @Test fun exampleCodecReadsPlainStringsAndQuotes() {
        val tricky = WordExample("She said \"hi\", then left.", "Cô ấy nói \"chào\".")
        assertEquals(tricky, ExampleCodec.decode(ExampleCodec.encode(tricky)))
        assertEquals(WordExample("Plain example from an older row.", ""), ExampleCodec.decode("Plain example from an older row."))
        assertEquals(WordExample("{not json", ""), ExampleCodec.decode("{not json"))
    }

    @Test fun reviewSchedulesAndCountsDueWords() = runBlocking<Unit> {
        listOf("hello", "bank").forEach { cache(it) }
        reviews.review("alice", "hello", Sm2.KNOWN)
        reviews.review("alice", "bank", Sm2.NOT_KNOWN)
        assertEquals(WordLearningStatus.MASTERED, reviews.observe("alice", "Hello").first()!!.status)
        assertEquals(0, reviews.observeDueCount("alice").first())
        now += 24 * 60 * 60 * 1000L
        assertEquals(2, reviews.observeDueCount("alice").first())
        assertEquals(setOf("hello", "bank"), reviews.dueWords("alice").toSet())
        val second = reviews.review("alice", "hello", Sm2.KNOWN)
        assertEquals(6, second.intervalDays)
        assertEquals(listOf("bank"), reviews.dueWords("alice"))
        assertEquals(2, count("word_progress"))
        fails<WordMissing> { reviews.review("alice", "never-cached", Sm2.KNOWN) }
    }

    @Test fun otherUsersDataIsRefused() = runBlocking<Unit> {
        cache("hello")
        fails<SessionRequired> { handbooks.create("bob", "Daily") }
        fails<SessionRequired> { notes.setNote("bob", "hello", "x") }
        fails<SessionRequired> { reviews.review("bob", "hello", Sm2.KNOWN) }
        assertThrows(SessionRequired::class.java) { handbooks.observeHandbooks("bob") }
        session.user.value = null
        fails<SessionRequired> { handbooks.create("alice", "Daily") }
    }
}
