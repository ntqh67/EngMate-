package com.engmate.app.feature.profile.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.engmate.app.feature.auth.presentation.LineIcon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
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
fun ProfileScreen(viewModel: ProfileViewModel, onSettings: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.editing) { if (!state.busy) viewModel.cancelEdit() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hồ sơ cá nhân", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (onSettings != null) TextButton(onClick = onSettings) { Text("⚙", fontSize = 24.sp) }
            }
            if (viewModel.isDemo) Text("Bản demo • Dữ liệu chỉ lưu trên thiết bị", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            when {
                state.user == null -> Text("Vui lòng đăng nhập để quản lý hồ sơ cá nhân.")
                state.loading -> CircularProgressIndicator()
                state.loadFailed -> Button(onClick = viewModel::retry) { Text("Thử tải lại") }
                state.editing && state.profile == null -> ProfileEditor(state, viewModel::change, viewModel::save, viewModel::cancelEdit)
                state.profile == null -> {
                    Text("Bạn chưa có hồ sơ trên thiết bị này.")
                    Text("Tạo hồ sơ để thiết lập trình độ và mục tiêu học mỗi ngày.")
                    Button(onClick = viewModel::edit, enabled = !state.busy) { Text("Tạo hồ sơ") }
                }
                else -> ProfileDetails(state, viewModel)

            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (state.confirmDelete) AlertDialog(
        onDismissRequest = viewModel::dismissDelete,
        title = { Text("Xóa hồ sơ trên thiết bị?") },
        text = { Text("Tên, ảnh, trình độ và mục tiêu trong hồ sơ sẽ bị xóa khỏi thiết bị này. Tài khoản đăng nhập Firebase không bị xóa. Bạn có thể tạo lại hồ sơ. Nếu còn dữ liệu học liên quan, thao tác xóa sẽ bị chặn để giữ lịch sử.") },
        confirmButton = { TextButton(onClick = viewModel::delete, enabled = !state.busy) { Text("Xóa hồ sơ") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDelete, enabled = !state.busy) { Text("Hủy") } },
    )
}

@Composable private fun ProfileDetails(state: ProfileUiState, vm: ProfileViewModel) {
    val profile = state.profile ?: return
    var photoDialog by remember { mutableStateOf(false) }
    var photoDraft by remember { mutableStateOf("") }
    val input = if (state.editing) state.input else ProfileInput(profile.displayName, profile.photoUrl.orEmpty(), profile.englishLevel, profile.dailyGoalMinutes.toString())
    fun update(next: ProfileInput) { if (!state.editing) vm.edit(); vm.change(next) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clickable(enabled = !state.busy, onClickLabel = "Đổi ảnh đại diện") { photoDraft = input.photoUrl; photoDialog = true }) { Avatar(profile) }
        Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(state.user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF565B62))
        Text("Người học", modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE6E5FE)).padding(horizontal = 16.dp, vertical = 6.dp), color = Color(0xFF694CCD), style = MaterialTheme.typography.labelMedium)
    }
    if (photoDialog) AlertDialog(onDismissRequest = { photoDialog = false }, title = { Text("Ảnh đại diện") },
        text = { OutlinedTextField(photoDraft, { photoDraft = it }, label = { Text("Đường dẫn ảnh HTTPS (tùy chọn)") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { update(input.copy(photoUrl = photoDraft)); photoDialog = false }) { Text("Xong") } },
        dismissButton = { TextButton(onClick = { photoDialog = false }) { Text("Hủy") } })
    state.errors[ProfileField.PHOTO]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(4.dp))
    Text("Tên hiển thị", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    OutlinedTextField(value = input.displayName, onValueChange = { update(input.copy(displayName = it)) }, modifier = Modifier.fillMaxWidth().testTag("profile_name"), singleLine = true, enabled = !state.busy,
        leadingIcon = { LineIcon("user", Color(0xFF3E4349)) }, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
        isError = state.errors[ProfileField.NAME] != null, supportingText = state.errors[ProfileField.NAME]?.let { error -> { Text(error) } })
    Text("Trình độ tiếng Anh", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    ProfileLevel(input.englishLevel, !state.busy) { update(input.copy(englishLevel = it)) }
    Text("Mục tiêu mỗi ngày", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    OutlinedTextField(value = input.dailyGoalMinutes, onValueChange = { update(input.copy(dailyGoalMinutes = it)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy,
        leadingIcon = { LineIcon("target", MaterialTheme.colorScheme.primary) }, suffix = { Text("phút/ngày") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
        isError = state.errors[ProfileField.GOAL] != null, supportingText = state.errors[ProfileField.GOAL]?.let { error -> { Text(error) } })
    Button(onClick = vm::save, enabled = state.editing && !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text(if (state.busy) "Đang lưu…" else "Lưu thay đổi") }
    if (state.editing) TextButton(onClick = vm::cancelEdit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Hủy") }
    TextButton(onClick = vm::askDelete, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Xóa hồ sơ trên thiết bị", color = MaterialTheme.colorScheme.error) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileLevel(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf("A1" to "Sơ cấp (A1)", "A2" to "Sơ cấp (A2)", "B1" to "Trung cấp (B1)", "B2" to "Trung cấp (B2)", "C1" to "Cao cấp (C1)", "C2" to "Cao cấp (C2)")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = labels[value] ?: value, onValueChange = {}, readOnly = true, enabled = enabled, modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            leadingIcon = { LineIcon("chart", MaterialTheme.colorScheme.primary) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProfileValidator.levels.forEach { level -> DropdownMenuItem(text = { Text(labels[level] ?: level) }, onClick = { onChange(level); expanded = false }) }
        }
    }
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
        LineIcon("user", Color(0xFF2C71B7))
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
