package com.engmate.app.feature.notebook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.notebook.data.RoomHandbookRepository
import com.engmate.app.feature.notebook.presentation.NotebookViewModel
import com.engmate.app.feature.profile.data.local.UserProfileEntity
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
class NotebookViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: EngMateDatabase
    private val session = object : UserSession {
        override val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
        override val isDemo = true
    }

    @Before fun setup() = runBlocking<Unit> {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EngMateDatabase::class.java)
            .allowMainThreadQueries().build()
        db.profiles().insert(UserProfileEntity("alice", "alice@example.com", "An", null, "A2", 15, 1, 1))
    }
    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun viewModel() = NotebookViewModel(session, RoomHandbookRepository(db.flashcards(), db.vocabulary(), db.notebook(), session, Clock.systemUTC()))

    /** Room runs queries on its own executor; wait for the real result instead of a fixed delay. */
    private fun <T> await(read: () -> T, done: (T) -> Boolean): T {
        val end = System.currentTimeMillis() + 5_000
        while (true) {
            val value = read()
            if (done(value) || System.currentTimeMillis() > end) return value
            Thread.sleep(20)
        }
    }

    @Test fun createShowsValidationErrorsAndKeepsDialogOpen() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        vm.showCreate()
        vm.create()
        assertEquals("Nhập tên sổ tay", await({ vm.dialog.value }) { it?.error != null }?.error)

        vm.onTitleChange("  Daily  ")
        assertNull("typing clears the error", vm.dialog.value?.error)
        vm.create()
        await({ vm.dialog.value }) { it == null }
        assertNull(vm.dialog.value)
        assertEquals("Đã tạo sổ tay “Daily”", vm.message.value)
        assertEquals(listOf("Daily"), await({ vm.state.value.handbooks }) { it.isNotEmpty() }.map { it.title })

        vm.showCreate(); vm.onTitleChange("DAILY"); vm.create()
        assertEquals("Tên sổ tay đã tồn tại", await({ vm.dialog.value }) { it?.error != null }?.error)
    }

    @Test fun openShowsEntriesAndDeleteReturnsToList() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val repo = RoomHandbookRepository(db.flashcards(), db.vocabulary(), db.notebook(), session, Clock.systemUTC())
        val id = runBlocking { repo.create("alice", "Work") }
        vm.open(id)
        assertEquals("Work", await({ vm.state.value.opened }) { it != null }?.title)
        vm.deleteOpened()
        await({ vm.state.value }) { it.opened == null && it.handbooks.isEmpty() }
        assertNull(vm.state.value.opened)
        assertTrue(runBlocking { repo.observeHandbooks("alice").first() }.isEmpty())
    }

    @Test fun signedOutShowsNothing() = runTest(dispatcher) {
        session.user.value = null
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.handbooks.isEmpty())
    }
}
