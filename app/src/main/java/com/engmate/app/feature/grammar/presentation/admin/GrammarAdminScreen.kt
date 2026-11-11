package com.engmate.app.feature.grammar.presentation.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.core.ui.components.*
import com.engmate.app.feature.grammar.domain.*

@Composable
fun GrammarAdminScreen(viewModel: GrammarAdminViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    BackHandler(enabled = state.editing) { viewModel.cancelEdit() }

    Scaffold(
        topBar = {
            BackTopBar(if (!state.editing) "Bài ngữ pháp" else if (state.editingId == null) "Thêm bài ngữ pháp" else "Sửa bài ngữ pháp",
                onBack = if (state.editing) viewModel::cancelEdit else onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.editing) ExtendedFloatingActionButton(onClick = viewModel::add) { Text("+ Thêm bài") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.editing) LessonForm(state, viewModel) else LessonList(state, viewModel)
        }
    }
    state.confirmDelete?.let { lesson ->
        ConfirmDeleteDialog("Xoá bài ngữ pháp?",
            "Bài \"${lesson.title}\" và toàn bộ câu hỏi của bài sẽ bị xoá. Không thể xoá nếu đã có người làm bài tập của bài này.",
            state.busy, viewModel::delete, viewModel::dismissDelete)
    }
}

@Composable
private fun LessonList(state: GrammarAdminUiState, viewModel: GrammarAdminViewModel) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.lessons.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text("Chưa có bài ngữ pháp nào. Bấm \"+ Thêm bài\" để tạo bài đầu tiên.")
        }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.lessons, key = { it.id }) { lesson ->
                Card(Modifier.fillMaxWidth().clickable { viewModel.edit(lesson) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(lesson.level) })
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f))
                        }
                        lesson.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Text("${lesson.examples.size} ví dụ", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.edit(lesson) }, enabled = !state.busy) { Text("Sửa") }
                            TextButton(onClick = { viewModel.askDelete(lesson) }, enabled = !state.busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xoá") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonForm(state: GrammarAdminUiState, viewModel: GrammarAdminViewModel) {
    val input = state.input
    val enabled = !state.busy
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(input.title, { viewModel.change(input.copy(title = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Tiêu đề *") }, singleLine = true, enabled = enabled,
            isError = LessonField.TITLE in state.errors, supportingText = state.errors[LessonField.TITLE]?.let { { Text(it) } })
        Text("Trình độ *", style = MaterialTheme.typography.labelLarge)
        ChipRow(LessonValidator.levels, input.level, { it }, { viewModel.change(input.copy(level = it)) }, enabled = enabled)
        FieldError(state.errors[LessonField.LEVEL])
        OutlinedTextField(input.summary, { viewModel.change(input.copy(summary = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Tóm tắt") }, minLines = 2, enabled = enabled)
        OutlinedTextField(input.content, { viewModel.change(input.copy(content = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Nội dung *") }, minLines = 6, enabled = enabled,
            isError = LessonField.CONTENT in state.errors, supportingText = state.errors[LessonField.CONTENT]?.let { { Text(it) } })

        Text("Ví dụ", style = MaterialTheme.typography.titleSmall)
        input.examples.forEachIndexed { index, example ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun set(value: GrammarExample) = viewModel.change(input.copy(examples = input.examples.toMutableList().also { it[index] = value }))
                    OutlinedTextField(example.en, { set(example.copy(en = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("Câu tiếng Anh") }, enabled = enabled)
                    OutlinedTextField(example.vi, { set(example.copy(vi = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("Nghĩa tiếng Việt") }, enabled = enabled)
                    TextButton(onClick = { viewModel.change(input.copy(examples = input.examples.filterIndexed { i, _ -> i != index })) },
                        enabled = enabled, modifier = Modifier.align(Alignment.End)) { Text("Xoá ví dụ") }
                }
            }
        }
        FieldError(state.errors[LessonField.EXAMPLES])
        OutlinedButton(onClick = { viewModel.change(input.copy(examples = input.examples + GrammarExample("", ""))) }, enabled = enabled) {
            Text("+ Thêm ví dụ")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::save, enabled = enabled) { Text(if (state.busy) "Đang lưu…" else "Lưu") }
            OutlinedButton(onClick = viewModel::cancelEdit, enabled = enabled) { Text("Huỷ") }
        }
    }
}
