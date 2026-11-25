package com.engmate.app.feature.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.BuildConfig
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding

/** Cài đặt: learning shortcuts, then the account rows; destructive rows ask first. */
@Composable
fun SettingsScreen(
    profile: ProfileViewModel,
    email: String,
    onBack: () -> Unit,
    onPlacement: () -> Unit,
    onReminder: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by profile.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Cài đặt", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = screenPadding)
            .padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            state.message?.let { FeedbackBanner(it, kind = FeedbackKind.SUCCESS) }
            SectionHeader("Học tập", Modifier.padding(top = 0.dp))
            SurfaceGroup {
                SettingRow(R.drawable.ic_target, "Kiểm tra trình độ", "15 câu để xác định trình độ A1–B2", onPlacement)
                GroupDivider(56.dp)
                SettingRow(R.drawable.ic_notifications, "Nhắc học hằng ngày", "Chọn giờ nhận thông báo", onReminder)
            }
            SectionHeader("Tài khoản")
            SurfaceGroup {
                ListRow(email, supporting = "Email đăng nhập", headlineLines = 1, leading = { Icon(painterResource(R.drawable.ic_mail), null) })
                GroupDivider(56.dp)
                SettingRow(R.drawable.ic_edit, "Sửa hồ sơ", null, onEditProfile)
                if (state.profile != null) {
                    GroupDivider(56.dp)
                    DangerRow(R.drawable.ic_delete, "Xóa hồ sơ trên thiết bị", enabled = !state.busy, onClick = profile::askDelete)
                }
                GroupDivider(56.dp)
                DangerRow(R.drawable.ic_logout, "Đăng xuất", onClick = { confirmSignOut = true })
            }
            Text("Phiên bản ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.l))
        }
    }
    ProfileDeleteDialog(state, profile)
    if (confirmSignOut) ConfirmDialog("Đăng xuất?", "Dữ liệu học đã được lưu và đồng bộ; đăng nhập lại để tiếp tục.", "Đăng xuất",
        onConfirm = { confirmSignOut = false; onSignOut() }, onDismiss = { confirmSignOut = false })
}

@Composable
private fun SettingRow(icon: Int, title: String, body: String?, onClick: () -> Unit) {
    ListRow(title, supporting = body, onClick = onClick, headlineLines = 1,
        leading = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) }, trailing = { RowChevron() })
}

@Composable
private fun DangerRow(icon: Int, title: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Destructive rows use the error color for both icon and label.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(icon), null)
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Confirmation for deleting the device profile; driven by [ProfileUiState.confirmDelete]. */
@Composable
fun ProfileDeleteDialog(state: ProfileUiState, vm: ProfileViewModel) {
    if (state.confirmDelete) ConfirmDialog("Xóa hồ sơ trên thiết bị?",
        "Tên, ảnh, trình độ và mục tiêu sẽ bị xoá khỏi thiết bị này; tài khoản đăng nhập vẫn giữ nguyên và bạn có thể tạo lại hồ sơ. " +
            "Nếu còn dữ liệu học liên quan, thao tác sẽ bị chặn để giữ lịch sử.",
        "Xóa hồ sơ", onConfirm = vm::delete, onDismiss = vm::dismissDelete, busy = state.busy)
}
