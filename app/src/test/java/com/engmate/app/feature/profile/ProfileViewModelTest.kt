package com.engmate.app.feature.profile

import com.engmate.app.feature.auth.domain.*
import com.engmate.app.feature.profile.domain.*
import com.engmate.app.feature.profile.presentation.ProfileViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : UserSession {
        override val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
        override val isDemo = true
    }
    private class FakeRepository : ProfileRepository {
        val data = MutableStateFlow<UserProfile?>(null)
        var fail = false
        var writes = 0
        var deletes = 0
        override fun observe(uid: String) = data.map { it?.takeIf { p -> p.uid == uid } }
        override suspend fun create(uid: String, input: ProfileInput) {
            writes++
            if (fail) error("disk failure")
            data.value = UserProfile(uid, "alice@example.com", input.displayName, null, input.englishLevel, input.dailyGoalMinutes.toInt(), 1, 1)
        }
        override suspend fun update(uid: String, input: ProfileInput) = create(uid, input)
        override suspend fun delete(uid: String) { deletes++; data.value = null }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun invalidFormNeverWritesAndFailurePreservesInputForRetry() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = ProfileViewModel(repo, session)
        runCurrent()
        vm.edit(); vm.change(ProfileInput("A")); vm.save(); runCurrent()
        assertEquals(0, repo.writes)
        assertTrue(vm.state.value.errors.isNotEmpty())
        vm.change(ProfileInput("Nguyễn An")); repo.fail = true; vm.save(); runCurrent()
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.editing)
        assertEquals("Nguyễn An", vm.state.value.input.displayName)
        repo.fail = false; vm.save(); vm.save(); runCurrent()
        assertEquals(2, repo.writes)
        assertFalse(vm.state.value.editing)
        assertEquals("Nguyễn An", vm.state.value.profile!!.displayName)
    }
    @Test fun deleteRequiresConfirmationAndSwitchClearsEditorAndProfile() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = ProfileViewModel(repo, session)
        runCurrent()
        vm.edit(); vm.change(ProfileInput("Nguyễn An")); vm.save(); runCurrent()
        vm.delete(); runCurrent(); assertEquals(0, repo.deletes)
        vm.askDelete(); vm.dismissDelete(); vm.delete(); runCurrent(); assertEquals(0, repo.deletes)
        vm.askDelete(); vm.delete(); runCurrent(); assertEquals(1, repo.deletes)
        assertNull(vm.state.value.profile)
        vm.edit(); vm.change(ProfileInput("Nguyễn An")); vm.save(); runCurrent()
        vm.edit()
        session.user.value = SessionUser("bob", "bob@example.com"); runCurrent()
        assertNull(vm.state.value.profile)
        assertFalse(vm.state.value.editing)
        assertEquals("", vm.state.value.input.displayName)
        session.user.value = null; runCurrent()
        assertNull(vm.state.value.user)
        assertNull(vm.state.value.profile)
    }
}
