package com.engmate.app.feature.profile.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.profile.domain.*

/**
 * Sửa hồ sơ, and Tạo hồ sơ when there is none yet. Typing in any field starts editing; the sticky bar saves or cancels.
 * [onClose] shows a close arrow; [onLater] / [onSignOut] are offered when a profile is required before going on.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSettings: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onPlacement: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.editing) { if (!state.busy) viewModel.cancelEdit() }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(if (state.profile == null) "Hồ sơ của bạn" else "Sửa hồ sơ", onClose, navigationIcon = R.drawable.ic_close, navigationLabel = "Đóng") {
                if (onLater != null) TextButton(onClick = onLater) { Text("Để sau") }
                if (onSettings != null) TopBarAction(R.drawable.ic_settings, "Cài đặt", onSettings)
                if (onSignOut != null) Box {
                    TopBarAction(R.drawable.ic_more, "Tuỳ chọn khác", { menu = true })
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Đăng xuất") }, onClick = { menu = false; onSignOut() }) }
                }
            }
        },
        bottomBar = {
            // Always present once there is a form: Lưu stays disabled until something changes; Hủy leaves when nothing did.
            if (state.editing || (state.profile != null && !state.loading)) StickyBottomBar {
                ButtonPair("Hủy", { if (state.editing) viewModel.cancelEdit() else onClose?.invoke() },
                    if (state.busy) "Đang lưu…" else "Lưu hồ sơ", viewModel::save, primaryEnabled = state.editing, loading = state.busy)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = screenPadding).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            state.message?.let { FeedbackBanner(it, kind = FeedbackKind.SUCCESS) }
            when {
                state.user == null -> EmptyState("Bạn chưa đăng nhập", "Đăng nhập để quản lý hồ sơ cá nhân.", icon = R.drawable.ic_lock)
                state.loading -> LoadingState(rows = 3, hero = false)
                state.loadFailed -> ErrorState("Không tải được hồ sơ.", onRetry = viewModel::retry)
                state.profile == null && !state.editing -> EmptyState("Tạo hồ sơ để bắt đầu",
                    "Đặt tên, trình độ và mục tiêu mỗi ngày để EngMate gợi ý bài phù hợp.", icon = R.drawable.ic_person,
                    action = { PrimaryButton("Tạo hồ sơ", viewModel::edit, enabled = !state.busy) })
                else -> ProfileForm(state, viewModel, onPlacement)
            }
        }
    }
}

@Composable
private fun ProfileForm(state: ProfileUiState, vm: ProfileViewModel, onPlacement: (() -> Unit)?) {
    val profile = state.profile
    val input = if (state.editing || profile == null) state.input
        else ProfileInput(profile.displayName, profile.photoUrl.orEmpty(), profile.englishLevel, profile.dailyGoalMinutes.toString())
    fun update(next: ProfileInput) { if (!state.editing) vm.edit(); vm.change(next) }
    var photoDialog by remember { mutableStateOf(false) }
    var photoDraft by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.clip(CircleShape).clickable(enabled = !state.busy, onClickLabel = "Đổi ảnh đại diện") { photoDraft = input.photoUrl; photoDialog = true }) {
            Avatar(input.photoUrl.ifBlank { null }, 88.dp)
        }
        TextButton(onClick = { photoDraft = input.photoUrl; photoDialog = true }, enabled = !state.busy) { Text("Đổi ảnh đại diện") }
        state.user?.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    state.errors[ProfileField.PHOTO]?.let { FieldError(it) }

    InfoCard {
        OutlinedTextField(input.displayName, { update(input.copy(displayName = it)) }, Modifier.fillMaxWidth().testTag("profile_name"),
            label = { Text("Họ tên") }, singleLine = true, enabled = !state.busy, shape = MaterialTheme.shapes.medium,
            leadingIcon = { Icon(painterResource(R.drawable.ic_person), null) },
            isError = state.errors[ProfileField.NAME] != null, supportingText = state.errors[ProfileField.NAME]?.let { e -> { Text(e) } })
        HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Trình độ tiếng Anh", style = MaterialTheme.typography.titleSmall)
        ChipRow(ProfileValidator.levels, input.englishLevel, { it }, { update(input.copy(englishLevel = it)) }, enabled = !state.busy)
        state.errors[ProfileField.LEVEL]?.let { FieldError(it) }
        if (onPlacement != null) Row(Modifier.fillMaxWidth().clickable(onClick = onPlacement).heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_target), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.s))
            Text("Chưa biết trình độ? Làm bài kiểm tra 15 câu", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            RowChevron()
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Mục tiêu mỗi ngày", style = MaterialTheme.typography.titleSmall)
        val goals = (listOf("10", "15", "30", "45", "60") + input.dailyGoalMinutes).filter { it.toIntOrNull() != null }.distinct().sortedBy { it.toInt() }
        ChipRow(goals, input.dailyGoalMinutes, { "$it phút" }, { update(input.copy(dailyGoalMinutes = it)) }, enabled = !state.busy)
        state.errors[ProfileField.GOAL]?.let { FieldError(it) }
    }

    if (photoDialog) AppDialog("Ảnh đại diện", { photoDialog = false }, "Xong", { update(input.copy(photoUrl = photoDraft)); photoDialog = false }) {
        OutlinedTextField(photoDraft, { photoDraft = it }, label = { Text("Đường dẫn ảnh HTTPS (tuỳ chọn)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    }
}

/** Round avatar: the photo when there is one, otherwise a person icon on the brand tint. */
@Composable
internal fun Avatar(photoUrl: String?, size: Dp) {
    val modifier = Modifier.size(size).clip(CircleShape)
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
            Icon(painterResource(R.drawable.ic_person), null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    if (photoUrl == null) Box(modifier) { placeholder() }
    else SubcomposeAsyncImage(model = photoUrl, contentDescription = "Ảnh đại diện", modifier = modifier, contentScale = ContentScale.Crop,
        loading = { placeholder() }, error = { placeholder() })
}
