package com.engmate.app.feature.notebook.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.ConfirmDeleteDialog
import com.engmate.app.core.ui.components.EmptyState
import com.engmate.app.core.ui.components.MessageEffect
import com.engmate.app.feature.notebook.domain.Handbook
import com.engmate.app.feature.notebook.domain.HandbookEntry
import com.engmate.app.feature.notebook.domain.NotebookRules

@Composable
fun NotebookScreen(viewModel: NotebookViewModel, onOpenWord: (String) -> Unit = {}, onStudy: (Long) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = state.opened != null) { viewModel.close() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val opened = state.opened
            if (opened == null) HandbookList(state, viewModel::open, viewModel::showCreate)
            else HandbookDetail(opened, state.entries, viewModel::close, onDelete = { confirmDelete = true }, onOpenWord = onOpenWord, onStudy = { onStudy(opened.id) })
        }
    }

    dialog?.let { d ->
        AlertDialog(onDismissRequest = viewModel::dismissCreate,
            title = { Text("Tạo sổ tay mới") },
            text = {
                OutlinedTextField(d.title, viewModel::onTitleChange, Modifier.fillMaxWidth().testTag("handbook_title"),
                    label = { Text("Tên sổ tay") }, placeholder = { Text("Từ vựng phỏng vấn") }, singleLine = true,
                    isError = d.error != null, enabled = !d.busy,
                    supportingText = { Text(d.error ?: "Tối đa ${NotebookRules.MAX_TITLE} ký tự") })
            },
            confirmButton = { TextButton(onClick = viewModel::create, enabled = !d.busy) { Text("Tạo") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCreate, enabled = !d.busy) { Text("Huỷ") } })
    }
    if (confirmDelete) state.opened?.let { h ->
        ConfirmDeleteDialog("Xoá “${h.title}”?", "${h.wordCount} từ sẽ được gỡ khỏi sổ tay này. Từ, ghi chú và tiến độ ôn của bạn vẫn được giữ.",
            busy = false, onConfirm = { confirmDelete = false; viewModel.deleteOpened() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun HandbookList(state: NotebookUiState, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Vocab Handbook", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        when {
            state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.failed -> item { Text("Không tải được sổ tay. Hãy mở lại ứng dụng.", color = MaterialTheme.colorScheme.error) }
            state.handbooks.isEmpty() -> item {
                EmptyState("Tạo sổ tay đầu tiên", "Gom các từ bạn muốn nhớ vào sổ tay để ôn bằng flashcard.")
            }
            else -> items(state.handbooks, key = { it.id }) { HandbookCard(it) { onOpen(it.id) } }
        }
        item {
            OutlinedButton(onClick = onCreate, Modifier.fillMaxWidth().testTag("create_handbook")) {
                Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text("Tạo sổ tay mới")
            }
        }
    }
}

@Composable
private fun HandbookCard(handbook: Handbook, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(handbook.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${handbook.wordCount} thẻ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator({ if (handbook.wordCount == 0) 0f else handbook.knownCount.toFloat() / handbook.wordCount },
                    Modifier.weight(1f))
                Text("${handbook.knownCount}/${handbook.wordCount} đã thuộc", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HandbookDetail(handbook: Handbook, entries: List<HandbookEntry>, onBack: () -> Unit, onDelete: () -> Unit,
                           onOpenWord: (String) -> Unit, onStudy: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(handbook.title, onBack) { TextButton(onClick = onDelete) { Text("Xoá", color = MaterialTheme.colorScheme.error) } }
        if (entries.isEmpty()) EmptyState("Sổ tay còn trống", "Tra từ ở Trang chủ rồi thêm vào sổ tay này.")
        else LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
            item {
                Button(onClick = onStudy, Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("study_handbook")) { Text("Học flashcard") }
            }
            items(entries, key = { it.cardId }) { entry ->
                ListItem(modifier = Modifier.clickable { onOpenWord(entry.word) }, headlineContent = { Text(entry.word, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(entry.displayMeaning, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { if (entry.status == WordLearningStatus.MASTERED) Text("Đã thuộc", style = MaterialTheme.typography.labelMedium) })
                HorizontalDivider()
            }
        }
    }
}
