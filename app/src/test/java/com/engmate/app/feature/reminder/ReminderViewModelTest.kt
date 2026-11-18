package com.engmate.app.feature.reminder

import com.engmate.app.feature.reminder.data.ReminderRepository
import com.engmate.app.feature.reminder.domain.ReminderScheduler
import com.engmate.app.feature.reminder.presentation.ReminderUiState
import com.engmate.app.feature.reminder.presentation.ReminderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

/** ReminderViewModel with a real DataStore file and a fake scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var temp: TempDataStore

    private class FakeScheduler : ReminderScheduler {
        val calls = mutableListOf<String>()
        override fun schedule(hour: Int, minute: Int) { calls += "schedule $hour:$minute" }
        override fun cancel() { calls += "cancel" }
    }

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); temp = TempDataStore(folder) }
    @After fun tearDown() { temp.close(); Dispatchers.resetMain() }

    private suspend fun ReminderViewModel.waitFor(condition: (ReminderUiState) -> Boolean): ReminderUiState =
        withTimeout(5_000) { state.first(condition) }

    /** Waits until a change has finished and its message was shown, then clears the message. */
    private suspend fun ReminderViewModel.settled(): ReminderUiState =
        waitFor { !it.busy && !it.loading }.also { messageShown() }

    @Test fun enablingSchedulesChangingTimeReschedulesDisablingCancels() = runBlocking {
        val scheduler = FakeScheduler()
        val vm = ReminderViewModel(ReminderRepository(temp.store), scheduler)
        vm.waitFor { !it.loading }

        vm.setEnabled(true)
        vm.waitFor { it.settings.enabled && !it.busy }
        assertEquals("Đã lưu", vm.state.value.message); vm.messageShown()
        assertEquals(listOf("schedule 20:0"), scheduler.calls)

        vm.setTime(7, 30)
        vm.waitFor { it.settings.hour == 7 && it.settings.minute == 30 && !it.busy }
        assertEquals(listOf("schedule 20:0", "schedule 7:30"), scheduler.calls)

        vm.setEnabled(false)
        vm.waitFor { !it.settings.enabled && !it.busy }
        assertEquals(listOf("schedule 20:0", "schedule 7:30", "cancel"), scheduler.calls)
    }

    @Test fun changingTimeWhileOffOnlySavesIt() = runBlocking {
        val scheduler = FakeScheduler()
        val vm = ReminderViewModel(ReminderRepository(temp.store), scheduler)
        vm.waitFor { !it.loading }
        vm.setTime(6, 45)
        vm.waitFor { it.settings.hour == 6 && it.settings.minute == 45 && !it.busy }
        vm.settled()
        assertTrue(scheduler.calls.isEmpty())
        assertFalse(vm.state.value.settings.enabled)
    }

    @Test fun refusedPermissionKeepsReminderOff() = runBlocking {
        val scheduler = FakeScheduler()
        val vm = ReminderViewModel(ReminderRepository(temp.store), scheduler)
        vm.waitFor { !it.loading }
        vm.onPermissionDenied()
        val state = vm.waitFor { it.permissionDenied && !it.busy }
        assertFalse(state.settings.enabled)
        assertNull("no 'Đã lưu' for a refusal", state.message)
        assertEquals(listOf("cancel"), scheduler.calls)

        // Granting it later and switching on clears the warning.
        vm.setEnabled(true)
        val on = vm.waitFor { it.settings.enabled && !it.busy }
        assertFalse(on.permissionDenied)
    }
}
