package com.engmate.app.feature.vocabulary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.flashcard.data.RoomReviewRepository
import com.engmate.app.feature.flashcard.presentation.review.FlashcardViewModel
import com.engmate.app.feature.notebook.data.RoomHandbookRepository
import com.engmate.app.feature.notebook.data.RoomWordNoteRepository
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.RoomWordRepository
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.engmate.app.feature.vocabulary.presentation.worddetail.EditSheet
import com.engmate.app.feature.vocabulary.presentation.worddetail.WordDetailViewModel
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class WordDetailAndFlashcardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: EngMateDatabase
    private val session = object : UserSession {
        override val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
        override val isDemo = true
    }
    private val clock = Clock.systemUTC()
    private lateinit var words: RoomWordRepository
    private lateinit var notes: RoomWordNoteRepository
    private lateinit var handbooks: RoomHandbookRepository
    private lateinit var reviews: RoomReviewRepository

    @Before fun setup() = runBlocking<Unit> {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EngMateDatabase::class.java)
            .allowMainThreadQueries().build()
        db.profiles().insert(UserProfileEntity("alice", "alice@example.com", "An", null, "A2", 15, 1, 1))
        listOf("nuance" to "A minor distinction.", "hello" to "A greeting.", "bank" to "A money institution.").forEach { (w, m) ->
            db.vocabulary().insertWord(WordEntity(word = w, meaningEn = m, isCached = true))
        }
        words = RoomWordRepository(db.vocabulary(), clock)
        notes = RoomWordNoteRepository(db.notebook(), db.vocabulary(), session, clock)
        handbooks = RoomHandbookRepository(db.flashcards(), db.vocabulary(), db.notebook(), session, clock)
        reviews = RoomReviewRepository(db.flashcards(), db.vocabulary(), session, clock)
    }
    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    /** Room answers on its own executor; poll for the expected state instead of sleeping a fixed time. */
    private fun <T> await(read: () -> T, done: (T) -> Boolean): T {
        val end = System.currentTimeMillis() + 5_000
        while (true) { val v = read(); if (done(v) || System.currentTimeMillis() > end) return v; Thread.sleep(20) }
    }

    @Test fun wordDetailEditsOneCardAndSavesToHandbooks() = runTest(dispatcher) {
        val vm = WordDetailViewModel(session, words, notes, handbooks, object : com.engmate.app.feature.dictionary.domain.DictionaryRepository {
            override suspend fun lookup(raw: String, save: Boolean) = com.engmate.app.feature.dictionary.domain.LookupResult.NotFound
        })
        backgroundScope.launch { vm.state.collect {} }
        vm.open("Nuance")
        assertEquals("A minor distinction.", await({ vm.state.value.word }) { it != null }!!.meaningEn)

        vm.showSheet(EditSheet.EXAMPLE); vm.addExample(" ", "")
        assertEquals("Nhập câu ví dụ tiếng Anh", await({ vm.sheet.value?.error }) { it != null })
        vm.addExample("Mind the nuance.", "Chú ý sắc thái.")
        await({ vm.sheet.value }) { it == null }
        vm.showSheet(EditSheet.MEANING); vm.saveMeaning("sắc thái")
        assertEquals("sắc thái", await({ vm.state.value.customMeaning }) { it != null })
        assertEquals(listOf("Mind the nuance."), vm.state.value.note!!.examples.map { it.en })

        vm.showSheet(EditSheet.HANDBOOKS)
        vm.createHandbookAndAdd("IELTS")
        assertEquals(listOf("IELTS"), await({ vm.state.value.savedTitles }) { it.isNotEmpty() })
        assertNotNull("picker stays open after creating", vm.sheet.value)
        vm.createHandbookAndAdd("ielts")
        assertEquals("Tên sổ tay đã tồn tại", await({ vm.sheet.value?.error }) { it != null })
        val id = vm.state.value.savedIn.single()
        vm.toggleHandbook(id)
        await({ vm.state.value.savedIn }) { it.isEmpty() }
        assertTrue(vm.state.value.savedIn.isEmpty())
    }

    @Test fun flashcardRoundRecordsSm2AndRestartsWithUnknownCards() = runTest(dispatcher) {
        val id = runBlocking { handbooks.create("alice", "Daily").also { h -> listOf("nuance", "hello", "bank").forEach { handbooks.addWord("alice", h, it) } } }
        val vm = FlashcardViewModel(session, handbooks, words, notes, reviews)
        backgroundScope.launch { vm.state.collect {} }
        vm.start(id)
        assertEquals(listOf("nuance", "hello", "bank"), await({ vm.state.value.session }) { !it.loading }.cards)
        assertEquals("nuance", await({ vm.state.value.word }) { it != null }!!.word)

        vm.flip(); assertTrue(vm.state.value.session.flipped)
        vm.mark(true); vm.mark(false); vm.mark(true)
        val done = vm.state.value.session
        assertTrue(done.finished)
        assertEquals(2, done.knownCount)
        assertEquals(listOf("hello"), done.unknown)
        await({ runBlocking { reviews.observe("alice", "bank").first() } }) { it != null }
        assertEquals(WordLearningStatus.MASTERED, runBlocking { reviews.observe("alice", "nuance").first() }!!.status)
        assertEquals(WordLearningStatus.LEARNING, runBlocking { reviews.observe("alice", "hello").first() }!!.status)

        vm.restart(onlyUnknown = true)
        assertEquals(listOf("hello"), await({ vm.state.value.session }) { !it.loading && it.cards.size == 1 }.cards)
        assertFalse(vm.state.value.session.finished)
    }

    @Test fun emptyHandbookFinishesImmediately() = runTest(dispatcher) {
        val id = runBlocking { handbooks.create("alice", "Empty") }
        val vm = FlashcardViewModel(session, handbooks, words, notes, reviews)
        backgroundScope.launch { vm.state.collect {} }
        vm.start(id)
        val s = await({ vm.state.value.session }) { !it.loading }
        assertTrue(s.cards.isEmpty())
    }
}
