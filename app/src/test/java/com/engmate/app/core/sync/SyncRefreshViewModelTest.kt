package com.engmate.app.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRefreshViewModelTest {
    private class FakeRefresher(var result: RefreshResult = RefreshResult.DONE) : SyncRefresher {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun refresh(): RefreshResult { calls++; gate?.await(); return result }
    }

    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun secondTapWhileRefreshingIsIgnored() {
        val fake = FakeRefresher().apply { gate = CompletableDeferred() }
        val vm = SyncRefreshViewModel(fake)
        vm.refresh(); vm.refresh()
        assertTrue(vm.refreshing.value)
        assertEquals(1, fake.calls)
        fake.gate!!.complete(Unit)
        assertFalse(vm.refreshing.value)
        assertNull(vm.message.value)
    }

    @Test fun offlineAndFailureShowAMessageDoneAndThrottledDoNot() {
        val fake = FakeRefresher()
        val vm = SyncRefreshViewModel(fake)
        for ((result, shown) in listOf(RefreshResult.OFFLINE to true, RefreshResult.FAILED to true, RefreshResult.SIGNED_OUT to true,
            RefreshResult.DONE to false, RefreshResult.THROTTLED to false)) {
            fake.result = result
            vm.refresh()
            assertEquals("$result", shown, vm.message.value != null)
            assertFalse(vm.refreshing.value)
            vm.messageShown()
        }
    }
}
