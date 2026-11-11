package com.engmate.app.feature.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.auth.domain.*
import com.engmate.app.feature.profile.data.RoomProfileRepository
import com.engmate.app.feature.profile.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class RoomProfileRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: EngMateDatabase
    private val session = object : UserSession {
        override val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
        override val isDemo = true
    }
    private fun repository(time: Long = 1000) = RoomProfileRepository(db.profiles(), session,
        Clock.fixed(Instant.ofEpochMilli(time), ZoneOffset.UTC))
    @Before fun setup() {
        context.deleteDatabase("profile-test.db")
        db = Room.databaseBuilder(context, EngMateDatabase::class.java, "profile-test.db").build()
    }
    @After fun close() { db.close(); context.deleteDatabase("profile-test.db") }
    @Test fun crudPersistsAndPreservesIdentityAndCreationTime() = runBlocking {
        val initial = repository()
        assertNull(initial.observe("alice").first())
        initial.create("alice", ProfileInput(" Nguyễn An "))
        assertEquals("Nguyễn An", initial.observe("alice").first()!!.displayName)
        db.close()
        db = Room.databaseBuilder(context, EngMateDatabase::class.java, "profile-test.db").build()
        val reopened = repository(2000)
        assertNotNull(reopened.observe("alice").first())
        reopened.update("alice", ProfileInput("Nguyễn Bình", "https://example.com/avatar.png", "B2", "45"))
        val updated = reopened.observe("alice").first()!!
        assertEquals("alice@example.com", updated.email)
        assertEquals(1000L, updated.createdAt)
        assertEquals(2000L, updated.updatedAt)
        assertEquals(45, updated.dailyGoalMinutes)
        assertEquals("B2", updated.englishLevel)
        reopened.delete("alice")
        assertNull(reopened.observe("alice").first())
    }
    @Test fun duplicateCreationDoesNotOverwriteAndMissingUpdateDoesNotInsert() = runBlocking {
        val repo = repository()
        repo.create("alice", ProfileInput("Nguyễn An"))
        try { repo.create("alice", ProfileInput("Nguyễn Bình")); fail("Expected duplicate") } catch (_: ProfileAlreadyExists) { }
        assertEquals("Nguyễn An", repo.observe("alice").first()!!.displayName)
        repo.delete("alice")
        try { repo.update("alice", ProfileInput("Nguyễn Bình")); fail("Expected missing") } catch (_: ProfileMissing) { }
        assertNull(repo.observe("alice").first())
    }
    @Test fun validatesBeforeWritingAndSeparatesAccounts() = runBlocking {
        val repo = repository()
        try { repo.create("alice", ProfileInput("A", dailyGoalMinutes = "0")); fail("Expected validation") } catch (_: InvalidProfile) { }
        assertNull(repo.observe("alice").first())
        repo.create("alice", ProfileInput("Nguyễn An"))
        session.user.value = SessionUser("bob", "bob@example.com")
        repo.create("bob", ProfileInput("Trần Bình"))
        try { repo.observe("alice").first(); fail("Expected denied read") } catch (_: SessionRequired) { }
        try { repo.update("alice", ProfileInput("Trần Bình")); fail("Expected denied update") } catch (_: SessionRequired) { }
        try { repo.delete("alice"); fail("Expected denied delete") } catch (_: SessionRequired) { }
        repo.delete("bob")
        session.user.value = SessionUser("alice", "alice@example.com")
        assertEquals("Nguyễn An", repo.observe("alice").first()!!.displayName)
        session.user.value = null
        try { repo.delete("alice"); fail("Expected signed out") } catch (_: SessionRequired) { }
    }
}
