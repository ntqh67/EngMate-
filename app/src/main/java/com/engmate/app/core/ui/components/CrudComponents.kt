package com.engmate.app.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engmate.app.R

/** Top bar with an optional back arrow; icons are local vector drawables, so no icons artifact is needed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Quay lại") }
        },
        actions = actions,
    )
}

/** Section title used on list-style screens; extra space above separates it from the previous group. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Centered message for empty lists, with an optional next step. */
@Composable
fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(8.dp)); action() }
    }
}

/** Small tonal tag for CEFR levels (A1…C2). */
@Composable
fun LevelBadge(level: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(level, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

/** Shows a one-off message from UiState, then tells the ViewModel it was consumed. */
@Composable
fun MessageEffect(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message)
            onShown()
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, text: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xoá") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Huỷ") } },
    )
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) }, enabled = enabled)
        }
    }
}

@Composable
fun FieldError(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
