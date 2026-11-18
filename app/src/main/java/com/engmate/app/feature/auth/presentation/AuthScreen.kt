package com.engmate.app.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val blue = Color(0xFF2C71B7)
@Composable fun AuthScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.page == AuthPage.REGISTER) { RegisterScreen(state, vm); return }
    var passwordVisible by remember { mutableStateOf(false) }
    Scaffold(containerColor = Color(0xFFF3F9FF)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 700.dp
        Column(Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.page == AuthPage.RESET) {
                TextButton(onClick = { vm.page(AuthPage.SIGN_IN) }, modifier = Modifier.align(Alignment.Start)) { Text("←", fontSize = 24.sp) }
            } else Spacer(Modifier.height(if (compact) 20.dp else 40.dp))
            Text(buildAnnotatedString { withStyle(SpanStyle(color = Color(0xFF101419))) { append("Eng") }; withStyle(SpanStyle(color = blue)) { append("Mate") } }, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            if (state.page == AuthPage.RESET) {
                Spacer(Modifier.height(if (compact) 24.dp else 50.dp))
                Box(Modifier.size(104.dp).background(Color(0xFFE1F0FF), CircleShape), contentAlignment = Alignment.Center) { LineIcon("user", blue) }
                Spacer(Modifier.height(28.dp))
                Text("Quên mật khẩu?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Nhập email đã đăng ký để nhận\nliên kết đặt lại mật khẩu.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Color(0xFF3E4349))
            } else {
                Spacer(Modifier.height(30.dp))
                Text("Đăng nhập", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Chào mừng bạn quay trở lại!", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3E4349))
            }
            Spacer(Modifier.height(if (compact) 18.dp else 32.dp))
            if (!vm.session.isDemo || state.page == AuthPage.RESET) {
                RegisterField(state.email, { value -> vm.update { it.copy(email = value) } }, "Email", "email", state.busy, keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
            }
            if (state.page == AuthPage.SIGN_IN && !vm.session.isDemo) {
                Spacer(Modifier.height(12.dp))
                RegisterField(state.password, { value -> vm.update { it.copy(password = value) } }, "Mật khẩu", "lock", state.busy, passwordVisible, { passwordVisible = !passwordVisible }, KeyboardOptions(keyboardType = KeyboardType.Password))
                TextButton(onClick = { vm.page(AuthPage.RESET) }, modifier = Modifier.align(Alignment.End), enabled = !state.busy) { Text("Quên mật khẩu?", style = MaterialTheme.typography.labelLarge) }
            } else Spacer(Modifier.height(20.dp))
            state.message?.let { Text(it, color = if (state.error) MaterialTheme.colorScheme.error else blue, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) }
            Button(onClick = vm::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = blue)) {
                Text(if (state.busy) "Đang xử lý…" else if (state.page == AuthPage.RESET) "Gửi link đặt lại mật khẩu" else if (vm.session.isDemo) "Vào bản demo" else "Đăng nhập", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
            if (state.page == AuthPage.RESET) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFE4F5EA), RoundedCornerShape(12.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ⓘ", color = Color(0xFF1E8450))
                    Text("Bảo mật: link sẽ được gửi nếu email đã đăng ký hợp lệ.", color = Color(0xFF1E8450), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(30.dp))
                TextButton(onClick = { vm.page(AuthPage.SIGN_IN) }, enabled = !state.busy) { Text("← Quay lại Đăng nhập") }
            } else if (!vm.session.isDemo) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDDE4EB))
                    Text("  Hoặc tiếp tục với  ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF565B62))
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDDE4EB))
                }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Đăng nhập bằng Google (sắp có)") }
                Spacer(Modifier.height(if (compact) 18.dp else 38.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chưa có tài khoản?", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.page(AuthPage.REGISTER) }, enabled = !state.busy) { Text("Đăng ký ngay") }
                }
            } else Text("Bản demo chỉ hỗ trợ tài khoản minh họa trên thiết bị.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}
