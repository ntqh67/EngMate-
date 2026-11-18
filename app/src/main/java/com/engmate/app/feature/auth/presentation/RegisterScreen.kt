package com.engmate.app.feature.auth.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ink = Color(0xFF101419)
private val blue = Color(0xFF2C71B7)

@Composable
fun RegisterScreen(state: AuthState, vm: AuthViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    Scaffold(containerColor = Color(0xFFF3F9FF)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val compact = maxHeight < 700.dp
            Column(Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(if (compact) 12.dp else 30.dp))
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = ink)) { append("Eng") }
                    withStyle(SpanStyle(color = blue)) { append("Mate") }
                }, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
                Text("Đăng ký tài khoản", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
                Spacer(Modifier.height(6.dp))
                Text("Tạo tài khoản để bắt đầu học tiếng Anh", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3E4349), textAlign = TextAlign.Center)
                Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
                RegisterField(state.name, { value -> vm.update { it.copy(name = value) } }, "Họ và tên", "user", state.busy, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(12.dp))
                RegisterField(state.email, { value -> vm.update { it.copy(email = value) } }, "Email", "email", state.busy, keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(12.dp))
                RegisterField(state.password, { value -> vm.update { it.copy(password = value) } }, "Mật khẩu", "lock", state.busy, showPassword, { showPassword = !showPassword }, KeyboardOptions(keyboardType = KeyboardType.Password))
                Spacer(Modifier.height(12.dp))
                RegisterField(state.confirm, { value -> vm.update { it.copy(confirm = value) } }, "Nhập lại mật khẩu", "lock", state.busy, showConfirmation, { showConfirmation = !showConfirmation }, KeyboardOptions(keyboardType = KeyboardType.Password))
                state.message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = if (state.error) MaterialTheme.colorScheme.error else blue, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(if (compact) 16.dp else 26.dp))
                Button(onClick = vm::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = blue)) {
                    Text(if (state.busy) "Đang xử lý…" else "Đăng ký", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().background(Color(0xFFE1F0FF), RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ⓘ", color = blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Một email xác minh sẽ được gửi tới địa chỉ bạn đăng ký.", color = Color(0xFF0D4172), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(if (compact) 16.dp else 38.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("Đã có tài khoản?", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3E4349))
                    TextButton(onClick = { vm.page(AuthPage.SIGN_IN) }, enabled = !state.busy) { Text("Đăng nhập", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
internal fun RegisterField(value: String, onValueChange: (String) -> Unit, label: String, icon: String, busy: Boolean,
    visible: Boolean = false, toggleVisibility: (() -> Unit)? = null, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
        placeholder = { Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3E4349)) },
        leadingIcon = { LineIcon(icon, Color(0xFF3E4349)) },
        trailingIcon = if (toggleVisibility != null) ({ IconButton(onClick = toggleVisibility, modifier = Modifier.semantics { contentDescription = if (visible) "Ẩn $label" else "Hiện $label" }) { LineIcon("eye", Color(0xFF3E4349)) } }) else null,
        visualTransformation = if (toggleVisibility != null && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboard, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White,
            unfocusedBorderColor = Color(0xFFDDE4EB), focusedBorderColor = blue))
}

@Composable internal fun LineIcon(type: String, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val scale = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        val stroke = 1.8f * scale
        when(type) {
            "user" -> { drawCircle(color, 3.5f * scale, point(12f, 7f), style = Stroke(stroke)); drawArc(color, 180f, 180f, false, point(4f, 12f), Size(16f * scale, 16f * scale), style = Stroke(stroke, cap = StrokeCap.Round)) }
            "email" -> { drawRoundRect(color, point(3f, 5f), Size(18f * scale, 14f * scale), androidx.compose.ui.geometry.CornerRadius(2f * scale), style = Stroke(stroke)); drawLine(color, point(4f, 7f), point(12f, 13f), stroke); drawLine(color, point(12f, 13f), point(20f, 7f), stroke) }
            "lock" -> { drawRoundRect(color, point(5f, 10f), Size(14f * scale, 11f * scale), androidx.compose.ui.geometry.CornerRadius(2f * scale), style = Stroke(stroke)); drawArc(color, 180f, 180f, false, point(8f, 3f), Size(8f * scale, 14f * scale), style = Stroke(stroke)) }
            "chart" -> { drawLine(color, point(4f, 20f), point(21f, 20f), stroke); drawLine(color, point(7f, 17f), point(7f, 12f), stroke, cap = StrokeCap.Round); drawLine(color, point(12f, 17f), point(12f, 5f), stroke, cap = StrokeCap.Round); drawLine(color, point(17f, 17f), point(17f, 9f), stroke, cap = StrokeCap.Round) }
            "target" -> { drawCircle(color, 9f * scale, point(12f, 12f), style = Stroke(stroke)); drawCircle(color, 5f * scale, point(12f, 12f), style = Stroke(stroke)); drawCircle(color, 1.4f * scale, point(12f, 12f)) }
            "eye" -> { val path = Path().apply { moveTo(2f * scale, 12f * scale); quadraticTo(12f * scale, 2f * scale, 22f * scale, 12f * scale); quadraticTo(12f * scale, 22f * scale, 2f * scale, 12f * scale); close() }; drawPath(path, color, style = Stroke(stroke)); drawCircle(color, 2.5f * scale, point(12f, 12f), style = Stroke(stroke)) }
        }
    }
}
