package com.engmate.app.feature.reminder.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import androidx.compose.ui.Alignment
import com.engmate.app.feature.reminder.domain.formatTime
import com.engmate.app.feature.reminder.worker.ReminderWorker

/** Daily study reminder: on/off switch and time. Changes are saved right away. */
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit) {
    val viewModel: ReminderViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && ReminderWorker.canNotify(context)) viewModel.setEnabled(true) else viewModel.onPermissionDenied()
    }
    fun turnOn() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Notifications switched off for the whole app in system settings.
        !ReminderWorker.canNotify(context) -> viewModel.onPermissionDenied()
        else -> viewModel.setEnabled(true)
    }

    Scaffold(topBar = { AppTopBar("Nhắc học", onBack) }, snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // While off, the main action of the page is turning it on at the chosen time.
            if (!state.loading && !state.settings.enabled) StickyBottomBar {
                PrimaryButton("Bật nhắc học lúc ${state.settings.timeText}", { turnOn() }, Modifier.fillMaxWidth(), enabled = !state.busy, icon = R.drawable.ic_notifications)
            }
        }) { padding ->
        val s = state.settings
        val editable = !state.loading && !state.busy
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = screenPadding)
            .padding(bottom = Spacing.xl).widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            // Hero: the switch itself, on the brand color while the reminder is on.
            val brand = EngMateTheme.brand
            Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(),
                color = if (s.enabled) brand.brand else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (s.enabled) brand.onBrand else MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Icon(painterResource(R.drawable.ic_notifications), null)
                    Column(Modifier.weight(1f)) {
                        Text("Nhắc học hằng ngày", style = MaterialTheme.typography.titleMedium)
                        Text(if (s.enabled) "Nhắc lúc ${s.timeText} mỗi ngày" else "Đang tắt", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = s.enabled, enabled = editable, onCheckedChange = { on -> if (on) turnOn() else viewModel.setEnabled(false) })
                }
            }
            if (state.permissionDenied) FeedbackBanner("Bật quyền thông báo cho EngMate trong cài đặt, rồi bật lại công tắc.",
                kind = FeedbackKind.ERROR, title = "Chưa được phép gửi thông báo",
                action = { TextButton(onClick = { openNotificationSettings(context) }, contentPadding = PaddingValues(0.dp)) { Text("Mở cài đặt") } })
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                SectionHeader("Giờ nhắc")
                val quick = listOf(7 to 0, 12 to 0, 20 to 0, 21 to 30)
                val custom = (s.hour to s.minute) !in quick
                val options: List<Pair<Int, Int>?> = quick + listOf(null)
                ChipRow(options, if (custom) null else s.hour to s.minute,
                    { it?.let { (h, m) -> formatTime(h, m) } ?: if (custom) "Khác · ${s.timeText}" else "Khác" },
                    { pick -> if (pick == null) viewModel.openTimePicker() else viewModel.setTime(pick.first, pick.second) }, enabled = editable)
                SectionHeader("Xem trước thông báo")
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Icon(painterResource(R.drawable.ic_engmate), null, Modifier.size(20.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                        Text("EngMate · ${s.timeText}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Đến giờ học rồi!", style = MaterialTheme.typography.titleSmall)
                    Text("Dành vài phút ôn ngữ pháp và làm một bài quiz nhé.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Thông báo có thể đến trễ vài phút khi máy đang tiết kiệm pin.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (state.showTimePicker) TimeDialog(state.settings.hour, state.settings.minute,
        onConfirm = viewModel::setTime, onDismiss = viewModel::dismissTimePicker)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn giờ nhắc") },
        text = { TimePicker(picker) },
        confirmButton = { TextButton(onClick = { onConfirm(picker.hour, picker.minute) }) { Text("Lưu ${formatTime(picker.hour, picker.minute)}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) }
    catch (_: Exception) {
        // Some devices lack the notification page; fall back to the app details page.
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
