package com.engmate.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding

/**
 * Screen top bar. [title] may be null on detail screens whose heading lives in the content.
 * Root tabs pass [titleStyle] = headlineSmall for a larger heading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    @DrawableRes navigationIcon: Int = R.drawable.ic_arrow_back,
    navigationLabel: String = "Quay lại",
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { if (title != null) Text(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(navigationIcon), navigationLabel) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        modifier = modifier,
    )
}

/** Icon-only action for a top bar; [label] is read by TalkBack. */
@Composable
fun TopBarAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(painterResource(icon), label) }
}

/** Pill-shaped switch between two or three sections of the same screen. */
@Composable
fun AppSegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(Spacing.touch).clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceContainerHigh).padding(4.dp).selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) colors.surface else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.extraLarge).background(bg)
                    .selectable(isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Rounded search box with a clear button. [autoFocus] opens the keyboard on first show. */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onSearch: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(focus),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
        trailingIcon = if (value.isEmpty()) null else {
            { IconButton(onClick = { onValueChange("") }) { Icon(painterResource(R.drawable.ic_close), "Xoá nội dung tìm") } }
        },
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Main call to action: 52dp, full width by default. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier.heightIn(min = Spacing.button),
        shape = MaterialTheme.shapes.extraLarge, colors = colors) {
        ButtonContent(text, icon, loading)
    }
}

/** Secondary action next to or under a [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, @DrawableRes icon: Int? = null) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = Spacing.button), shape = MaterialTheme.shapes.extraLarge) {
        ButtonContent(text, icon, loading = false)
    }
}

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        Spacer(Modifier.width(Spacing.s))
    } else if (icon != null) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.s))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Fixed area at the bottom of a screen for its main action; respects the gesture bar.
 * [containerColor] lets the quiz tint it green or red for feedback.
 */
@Composable
fun StickyBottomBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), color = containerColor, contentColor = contentColor) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.navigationBarsPadding().padding(horizontal = screenPadding, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
        }
    }
}

/** Two buttons side by side in a [StickyBottomBar]: secondary on the left, primary on the right. */
@Composable
fun ButtonPair(secondary: String, onSecondary: () -> Unit, primary: String, onPrimary: () -> Unit,
               primaryEnabled: Boolean = true, loading: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SecondaryButton(secondary, onSecondary, Modifier.weight(1f), enabled = !loading)
        PrimaryButton(primary, onPrimary, Modifier.weight(1f), enabled = primaryEnabled, loading = loading)
    }
}

/** Material dialog with EngMate defaults. */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Hủy",
    destructive: Boolean = false,
    busy: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy, colors = if (destructive)
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(dismissLabel) } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Yes/no question before an action that is hard to undo. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
                  destructive: Boolean = true, busy: Boolean = false) {
    AppDialog(title, onDismiss, confirmLabel, onConfirm, destructive = destructive, busy = busy) { Text(text) }
}

/** Bottom sheet with a title row and close button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = screenPadding).padding(bottom = Spacing.xl).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), "Đóng") }
            }
            content()
        }
    }
}
