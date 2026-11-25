package com.engmate.app.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.engmate.app.core.ui.theme.Spacing

/** Top bar with an optional back arrow. Kept for existing screens; new code uses [AppTopBar]. */
@Composable
fun BackTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) =
    AppTopBar(title, onBack, actions = actions)

/**
 * Section title; more space above than below separates it from the previous group.
 * Pass [actionLabel] + [onAction] for a "Xem tất cả" link, or a custom [action].
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        action?.invoke()
    }
}

/** CEFR level tag; same as [LevelChip]. */
@Composable
fun LevelBadge(level: String, modifier: Modifier = Modifier) = LevelChip(level, modifier)

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
fun ConfirmDeleteDialog(title: String, text: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    ConfirmDialog(title, text, "Xoá", onConfirm, onDismiss, busy = busy)

/** One row of filter chips that scrolls sideways when it does not fit. */
@Composable
fun <T> ChipRow(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 40.dp), shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
        }
    }
}

@Composable
fun FieldError(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
