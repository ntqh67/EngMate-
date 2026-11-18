package com.engmate.app.feature.auth

import com.engmate.app.feature.auth.domain.*
import com.engmate.app.feature.auth.presentation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Fake : AuthService, UserSession {
        override val isDemo = false
        override val user = MutableStateFlow<SessionUser?>(null)
        var calls = 0
        override suspend fun signIn(email: String, password: String) { calls++; user.value = SessionUser("one", email) }
        override suspend fun register(name: String, email: String, password: String) { calls++ }
        override suspend fun resetPassword(email: String) { calls++ }
        override fun signOut() { user.value = null }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun invalidInputsNeverReachService() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake, fake)
        vm.page(AuthPage.REGISTER)
        vm.update { it.copy(name = "Nguyễn An", email = "an@example.com", password = "123456", confirm = "different") }
        vm.submit(); advanceUntilIdle()
        assertEquals(0, fake.calls); assertTrue(vm.state.value.error)
    }
    @Test fun registrationPromptsVerificationAndClearsPasswords() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake, fake)
        vm.page(AuthPage.REGISTER)
        vm.update { it.copy(name = "Nguyễn An", email = "an@example.com", password = "123456", confirm = "123456") }
        vm.submit(); advanceUntilIdle()
        assertEquals(1, fake.calls); assertEquals(AuthPage.SIGN_IN, vm.state.value.page)
        assertTrue(vm.state.value.message!!.contains("xác minh")); assertEquals("", vm.state.value.password)
    }
    @Test fun signOutClearsSession() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake, fake)
        vm.update { it.copy(email = "an@example.com", password = "123456") }
        vm.submit(); advanceUntilIdle(); assertNotNull(fake.user.value)
        vm.signOut(); assertNull(fake.user.value)
    }
}
