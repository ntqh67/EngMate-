package com.engmate.app.feature.notebook

import com.engmate.app.core.sync.HandbookGuard
import com.engmate.app.feature.notebook.data.GuardedHandbookRepository
import com.engmate.app.feature.notebook.domain.Handbook
import com.engmate.app.feature.notebook.domain.HandbookEntry
import com.engmate.app.feature.notebook.domain.HandbookMissing
import com.engmate.app.feature.notebook.domain.HandbookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GuardedHandbookRepositoryTest {
    private class FakeInner : HandbookRepository {
        val calls = mutableListOf<String>()
        override fun observeHandbooks(uid: String): Flow<List<Handbook>> = emptyFlow()
        override fun observeEntries(uid: String, handbookId: Long): Flow<List<HandbookEntry>> = emptyFlow()
        override fun observeHandbooksContaining(uid: String, word: String): Flow<Set<Long>> = emptyFlow()
        override suspend fun create(uid: String, title: String, description: String): Long { calls += "create"; return 1 }
        override suspend fun rename(uid: String, handbookId: Long, title: String) { calls += "rename" }
        override suspend fun delete(uid: String, handbookId: Long) { calls += "delete" }
        override suspend fun addWord(uid: String, handbookId: Long, word: String) { calls += "addWord" }
        override suspend fun removeWord(uid: String, handbookId: Long, word: String) { calls += "removeWord" }
    }

    private class FakeGuard(var deleted: Boolean) : HandbookGuard {
        val asked = mutableListOf<Long>()
        override suspend fun deletedElsewhere(handbookId: Long): Boolean { asked += handbookId; return deleted }
    }

    @Test fun editsGoThroughWhenTheHandbookStillExists() = runBlocking {
        val inner = FakeInner(); val guard = FakeGuard(deleted = false)
        val repo = GuardedHandbookRepository(inner, guard)
        repo.rename("u", 5, "x"); repo.addWord("u", 5, "bank"); repo.removeWord("u", 5, "bank")
        assertEquals(listOf("rename", "addWord", "removeWord"), inner.calls)
        assertEquals(listOf(5L, 5L, 5L), guard.asked)
    }

    @Test fun editsAreRefusedWhenAnotherDeviceDeletedTheHandbook() = runBlocking {
        val inner = FakeInner(); val repo = GuardedHandbookRepository(inner, FakeGuard(deleted = true))
        for (edit in listOf<suspend () -> Unit>({ repo.rename("u", 5, "x") }, { repo.addWord("u", 5, "bank") }, { repo.removeWord("u", 5, "bank") })) {
            try { edit(); fail("expected HandbookMissing") } catch (_: HandbookMissing) { }
        }
        assertTrue("nothing may reach the real repository", inner.calls.isEmpty())
    }

    @Test fun creatingAndDeletingAreNotChecked() = runBlocking {
        val inner = FakeInner(); val guard = FakeGuard(deleted = true)
        val repo = GuardedHandbookRepository(inner, guard)
        repo.create("u", "new"); repo.delete("u", 5)
        assertEquals(listOf("create", "delete"), inner.calls)
        assertTrue(guard.asked.isEmpty())
    }
}
