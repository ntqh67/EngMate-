package com.engmate.app.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*

/** Đăng nhập and Quên mật khẩu; Đăng ký is [RegisterScreen]. Behavior lives in [AuthViewModel] and is unchanged. */
@Composable fun AuthScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.page == AuthPage.REGISTER) { RegisterScreen(state, vm); return }
    var passwordVisible by remember { mutableStateOf(false) }
    val reset = state.page == AuthPage.RESET
    AuthFrame(
        title = if (reset) "Quên mật khẩu?" else "Đăng nhập",
        subtitle = if (reset) "Nhập email đã đăng ký để nhận liên kết đặt lại mật khẩu." else "Chào mừng bạn quay trở lại!",
        onBack = if (reset) ({ vm.page(AuthPage.SIGN_IN) }) else null,
    ) {
        if (!vm.session.isDemo || reset) {
            AuthField(state.email, { value -> vm.update { it.copy(email = value) } }, "Email", R.drawable.ic_mail, state.busy,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
        }
        if (!reset && !vm.session.isDemo) {
            AuthField(state.password, { value -> vm.update { it.copy(password = value) } }, "Mật khẩu", R.drawable.ic_lock, state.busy,
                passwordVisible, { passwordVisible = !passwordVisible }, KeyboardOptions(keyboardType = KeyboardType.Password))
            TextButton(onClick = { vm.page(AuthPage.RESET) }, modifier = Modifier.align(Alignment.End), enabled = !state.busy) { Text("Quên mật khẩu?") }
        }
        AuthMessage(state)
        PrimaryButton(
            when { state.busy -> "Đang xử lý…"; reset -> "Gửi liên kết đặt lại mật khẩu"; vm.session.isDemo -> "Vào bản demo"; else -> "Đăng nhập" },
            vm::submit, Modifier.fillMaxWidth(), loading = state.busy,
        )
        if (reset) FeedbackBanner("Vì lý do bảo mật, liên kết chỉ được gửi nếu email đã đăng ký.", kind = FeedbackKind.SUCCESS)
        else if (!vm.session.isDemo) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text("Chưa có tài khoản?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.page(AuthPage.REGISTER) }, enabled = !state.busy) { Text("Đăng ký ngay") }
        }
        else Text("Bản demo chỉ hỗ trợ tài khoản minh hoạ trên thiết bị.", style = MaterialTheme.typography.bodySmall)
    }
}
