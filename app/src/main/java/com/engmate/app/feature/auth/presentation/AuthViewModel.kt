package com.engmate.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.AuthService
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.profile.domain.ProfileInput
import com.engmate.app.feature.profile.domain.ProfileValidator
import com.engmate.app.feature.profile.domain.ProfileField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthPage { SIGN_IN, REGISTER, RESET }
data class AuthState(val page: AuthPage = AuthPage.SIGN_IN, val name: String = "", val email: String = "", val password: String = "", val confirm: String = "", val busy: Boolean = false, val message: String? = null, val error: Boolean = false)
@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthService, val session: UserSession) : ViewModel() {
    private val mutable = MutableStateFlow(AuthState())
    val state = mutable.asStateFlow()
    fun update(block: (AuthState) -> AuthState) { if (!mutable.value.busy) mutable.update { block(it).copy(message = null, error = false) } }
    fun page(page: AuthPage) = update { it.copy(page = page, password = "", confirm = "") }
    fun submit() {
        val s = mutable.value
        if (s.busy) return
        val email = s.email.trim()
        val isDemoEntry = session.isDemo && s.page == AuthPage.SIGN_IN
        val problem = when {
            isDemoEntry -> null
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Vui lòng nhập email hợp lệ."
            s.page == AuthPage.REGISTER && ProfileValidator.errors(ProfileInput(s.name)).containsKey(ProfileField.NAME) -> "Họ tên cần từ 2–50 ký tự chữ."
            s.page != AuthPage.RESET && s.password.length < 6 -> "Mật khẩu phải có ít nhất 6 ký tự."
            s.page == AuthPage.REGISTER && s.password != s.confirm -> "Mật khẩu nhập lại chưa khớp."
            else -> null
        }
        if (problem != null) { mutable.update { it.copy(message = problem, error = true) }; return }
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                when (s.page) {
                    AuthPage.SIGN_IN -> auth.signIn(email, s.password)
                    AuthPage.REGISTER -> auth.register(s.name.trim(), email, s.password)
                    AuthPage.RESET -> auth.resetPassword(email)
                }
                mutable.update { it.copy(busy = false, page = AuthPage.SIGN_IN, password = "", confirm = "", message = when(s.page) {
                    AuthPage.REGISTER -> "Đã tạo tài khoản. Hãy kiểm tra email và xác minh trước khi đăng nhập."
                    AuthPage.RESET -> "Nếu email hợp lệ, hãy kiểm tra hộp thư để đặt lại mật khẩu."
                    else -> null
                }) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                mutable.update { it.copy(busy = false, message = e.localizedMessage ?: "Không thể kết nối. Vui lòng thử lại.", error = true) }
            }
        }
    }
    fun signOut() { auth.signOut(); mutable.value = AuthState() }
}
