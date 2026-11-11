package com.engmate.app.feature.profile.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.engmate.app.feature.profile.domain.*

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.editing) { if (!state.busy) viewModel.cancelEdit() }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Hồ sơ cá nhân", style = MaterialTheme.typography.headlineMedium)
            if (viewModel.isDemo) Text("Bản demo • Dữ liệu chỉ lưu trên thiết bị", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            when {
                state.user == null -> Text("Vui lòng đăng nhập để quản lý hồ sơ cá nhân.")
                state.loading -> CircularProgressIndicator()
                state.loadFailed -> Button(onClick = viewModel::retry) { Text("Thử tải lại") }
                state.editing -> ProfileEditor(state, viewModel::change, viewModel::save, viewModel::cancelEdit)
                state.profile == null -> {
                    Text("Bạn chưa có hồ sơ trên thiết bị này.")
                    Text("Tạo hồ sơ để thiết lập trình độ và mục tiêu học mỗi ngày.")
                    Button(onClick = viewModel::edit, enabled = !state.busy) { Text("Tạo hồ sơ") }
                }
                else -> {
                    val profile = state.profile!!
                    Avatar(profile)
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProfileValue("Email tài khoản", state.user!!.email)
                            ProfileValue("Trình độ tiếng Anh", profile.englishLevel)
                            ProfileValue("Mục tiêu hằng ngày", "${profile.dailyGoalMinutes} phút")
                        }
                    }
                    Button(onClick = viewModel::edit, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Chỉnh sửa hồ sơ") }
                    OutlinedButton(onClick = viewModel::askDelete, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                        Text("Xóa hồ sơ trên thiết bị", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (state.confirmDelete) AlertDialog(
        onDismissRequest = viewModel::dismissDelete,
        title = { Text("Xóa hồ sơ trên thiết bị?") },
        text = { Text("Tên, ảnh, trình độ và mục tiêu trong hồ sơ sẽ bị xóa khỏi thiết bị này. Tài khoản đăng nhập Firebase không bị xóa. Bạn có thể tạo lại hồ sơ.") },
        confirmButton = { TextButton(onClick = viewModel::delete, enabled = !state.busy) { Text("Xóa hồ sơ") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDelete, enabled = !state.busy) { Text("Hủy") } },
    )
}

@Composable private fun ProfileValue(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodyLarge) }
}
@Composable private fun Avatar(profile: UserProfile) {
    val modifier = Modifier.size(88.dp).clip(CircleShape)
    if (profile.photoUrl == null) Initials(profile.displayName, modifier)
    else SubcomposeAsyncImage(model = profile.photoUrl, contentDescription = "Ảnh đại diện", modifier = modifier,
        contentScale = ContentScale.Crop, loading = { Initials(profile.displayName, Modifier.fillMaxSize()) },
        error = { Initials(profile.displayName, Modifier.fillMaxSize()) })
}
@Composable private fun Initials(name: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
    }
}
@Composable private fun ProfileEditor(state: ProfileUiState, change: (ProfileInput) -> Unit, save: () -> Unit, cancel: () -> Unit) {
    val input = state.input
    Text(if (state.profile == null) "Tạo hồ sơ" else "Chỉnh sửa hồ sơ", style = MaterialTheme.typography.titleLarge)
    Text(state.user!!.email, style = MaterialTheme.typography.bodyMedium)
    ProfileField("Họ tên", input.displayName, state.errors[ProfileField.NAME], !state.busy) { change(input.copy(displayName = it)) }
    ProfileField("Đường dẫn ảnh đại diện (HTTPS, tùy chọn)", input.photoUrl, state.errors[ProfileField.PHOTO], !state.busy,
        KeyboardType.Uri) { change(input.copy(photoUrl = it)) }
    Text("Trình độ tiếng Anh", style = MaterialTheme.typography.titleSmall)
    ProfileValidator.levels.chunked(3).forEach { levels ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            levels.forEach { level ->
                FilterChip(selected = input.englishLevel == level, onClick = { change(input.copy(englishLevel = level)) },
                    enabled = !state.busy, label = { Text(level) })
            }
        }
    }
    state.errors[ProfileField.LEVEL]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    ProfileField("Mục tiêu học (phút/ngày)", input.dailyGoalMinutes, state.errors[ProfileField.GOAL], !state.busy,
        KeyboardType.Number) { change(input.copy(dailyGoalMinutes = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = cancel, enabled = !state.busy) { Text("Hủy") }
        Button(onClick = save, enabled = !state.busy) { Text(if (state.busy) "Đang lưu…" else "Lưu hồ sơ") }
    }
}
@Composable private fun ProfileField(label: String, value: String, error: String?, enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, enabled = enabled, isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = { if (error != null) Text(error) })
}
