package com.engmate.app.feature.auth.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding

@Composable
fun RegisterScreen(state: AuthState, vm: AuthViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    AuthFrame(title = "Đăng ký tài khoản", subtitle = "Tạo tài khoản để bắt đầu học tiếng Anh") {
        AuthField(state.name, { value -> vm.update { it.copy(name = value) } }, "Họ và tên", R.drawable.ic_person, state.busy,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        AuthField(state.email, { value -> vm.update { it.copy(email = value) } }, "Email", R.drawable.ic_mail, state.busy,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
        AuthField(state.password, { value -> vm.update { it.copy(password = value) } }, "Mật khẩu", R.drawable.ic_lock, state.busy,
            showPassword, { showPassword = !showPassword }, KeyboardOptions(keyboardType = KeyboardType.Password))
        AuthField(state.confirm, { value -> vm.update { it.copy(confirm = value) } }, "Nhập lại mật khẩu", R.drawable.ic_lock, state.busy,
            showConfirmation, { showConfirmation = !showConfirmation }, KeyboardOptions(keyboardType = KeyboardType.Password))
        AuthMessage(state)
        PrimaryButton(if (state.busy) "Đang xử lý…" else "Đăng ký", vm::submit, Modifier.fillMaxWidth(), loading = state.busy)
        FeedbackBanner("Một email xác minh sẽ được gửi tới địa chỉ bạn đăng ký.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("Đã có tài khoản?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.page(AuthPage.SIGN_IN) }, enabled = !state.busy) { Text("Đăng nhập") }
        }
    }
}

/** Logo, heading and the form column shared by the sign-in, reset and register pages. */
@Composable
internal fun AuthFrame(title: String, subtitle: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = screenPadding).padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Quay lại Đăng nhập") }
                else Spacer(Modifier.height(Spacing.xl))
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("Eng") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Mate") }
                }, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.s))
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.s))
                content()
            }
        }
    }
}

/** Success or error message from [AuthState], with an icon so it does not rest on color. */
@Composable
internal fun AuthMessage(state: AuthState) {
    state.message?.let { FeedbackBanner(it, kind = if (state.error) FeedbackKind.ERROR else FeedbackKind.SUCCESS) }
}

@Composable
internal fun AuthField(value: String, onValueChange: (String) -> Unit, label: String, @DrawableRes icon: Int, busy: Boolean,
    visible: Boolean = false, toggleVisibility: (() -> Unit)? = null, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(icon), null) },
        trailingIcon = if (toggleVisibility != null) ({
            IconButton(onClick = toggleVisibility) { Icon(painterResource(R.drawable.ic_eye), if (visible) "Ẩn $label" else "Hiện $label") }
        }) else null,
        visualTransformation = if (toggleVisibility != null && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboard, shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface))
}
