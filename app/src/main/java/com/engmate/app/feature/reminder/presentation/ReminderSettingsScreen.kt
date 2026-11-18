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
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.MessageEffect
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

    Scaffold(topBar = { BackTopBar("Nhắc học hằng ngày", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val s = state.settings
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        leadingContent = { Icon(painterResource(R.drawable.ic_notifications), null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Nhắc học hằng ngày") },
                        supportingContent = { Text(if (s.enabled) "Nhắc lúc ${s.timeText} mỗi ngày" else "Đang tắt") },
                        trailingContent = {
                            Switch(checked = s.enabled, enabled = !state.loading && !state.busy,
                                onCheckedChange = { on -> if (on) turnOn() else viewModel.setEnabled(false) })
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        leadingContent = { Icon(painterResource(R.drawable.ic_schedule), null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Giờ nhắc") },
                        supportingContent = { Text(s.timeText) },
                        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable(enabled = !state.loading && !state.busy, onClick = viewModel::openTimePicker),
                    )
                }
            }
            if (state.permissionDenied) PermissionCard { openNotificationSettings(context) }
            Text("Thông báo có thể đến trễ vài phút khi máy đang tiết kiệm pin.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (state.showTimePicker) TimeDialog(state.settings.hour, state.settings.minute,
        onConfirm = viewModel::setTime, onDismiss = viewModel::dismissTimePicker)
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ứng dụng chưa được phép gửi thông báo nên chưa thể nhắc học.", color = MaterialTheme.colorScheme.error)
            Text("Bật quyền thông báo cho EngMate trong cài đặt, rồi bật lại công tắc.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenSettings) { Text("Mở cài đặt") }
        }
    }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
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
