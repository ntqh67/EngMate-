package com.engmate.app.feature.quiz.presentation.questionbank

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.core.ui.components.*
import com.engmate.app.feature.quiz.domain.*

private fun AnswerType.label() = when (this) { AnswerType.MULTIPLE_CHOICE -> "Trắc nghiệm"; AnswerType.FILL_BLANK -> "Điền từ" }

@Composable
fun QuestionBankScreen(viewModel: QuestionBankViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    val editor = state.editor
    BackHandler(enabled = editor != null) { viewModel.cancelEdit() }

    Scaffold(
        topBar = {
            BackTopBar(when { editor == null -> "Ngân hàng câu hỏi"; editor.id == null -> "Thêm câu hỏi"; else -> "Sửa câu hỏi" },
                onBack = if (editor != null) viewModel::cancelEdit else onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (editor == null) ExtendedFloatingActionButton(onClick = viewModel::add) { Text("+ Thêm câu hỏi") } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (editor != null) QuestionForm(editor, state, viewModel) else QuestionList(state, viewModel)
        }
    }
    state.confirmDelete?.let { q ->
        ConfirmDeleteDialog("Xoá câu hỏi?", "\"${q.content}\" và các đáp án sẽ bị xoá. Câu đã có trong lịch sử làm bài thì không thể xoá.",
            state.busy, viewModel::delete, viewModel::dismissDelete)
    }
}

@Composable
private fun QuestionList(state: QuestionBankUiState, viewModel: QuestionBankViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.query, viewModel::search, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Tìm theo nội dung hoặc đáp án") })
            ChipRow(listOf<QuestionCategory?>(null) + QuestionCategory.entries, state.category,
                { it?.label ?: "Tất cả" }, viewModel::filterCategory)
            when (state.category) {
                QuestionCategory.GRAMMAR -> ChipRow(listOf<Long?>(null) + state.lessons.map { it.id }, state.lessonFilter,
                    { id -> if (id == null) "Mọi bài" else state.lessonTitle(id).orEmpty() }, viewModel::filterLesson)
                QuestionCategory.VOCABULARY -> ChipRow(listOf<Long?>(null) + state.topics, state.topicFilter,
                    { id -> if (id == null) "Mọi chủ đề" else "Chủ đề #$id" }, viewModel::filterTopic)
                else -> Unit
            }
            Text("${state.visible.size} câu hỏi", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.visible.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(if (state.questions.isEmpty()) "Chưa có câu hỏi nào." else "Không có câu hỏi phù hợp bộ lọc.")
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visible, key = { it.id }) { q -> QuestionCard(q, state, viewModel) }
            }
        }
    }
}

@Composable
private fun QuestionCard(q: Question, state: QuestionBankUiState, viewModel: QuestionBankViewModel) {
    val scope = when (q.category) {
        QuestionCategory.GRAMMAR -> state.lessonTitle(q.lessonId) ?: "Bài #${q.lessonId}"
        QuestionCategory.VOCABULARY -> "Chủ đề #${q.topicId}"
        QuestionCategory.PLACEMENT -> PLACEMENT_TITLE
        null -> "Chưa phân loại"
    }
    Card(Modifier.fillMaxWidth().clickable { viewModel.edit(q) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$scope · ${q.answerType.label()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(q.content, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("Đáp án: " + q.correctOptions.joinToString(" / ") { it.content }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.edit(q) }, enabled = !state.busy) { Text("Sửa") }
                TextButton(onClick = { viewModel.askDelete(q) }, enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xoá") }
            }
        }
    }
}

@Composable
private fun QuestionForm(editor: QuestionEditor, state: QuestionBankUiState, viewModel: QuestionBankViewModel) {
    val enabled = !state.busy
    val errors = editor.errors
    fun change(value: QuestionEditor) = viewModel.change(value)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Loại câu hỏi *", style = MaterialTheme.typography.labelLarge)
        ChipRow(QuestionCategory.entries, editor.category, { it.label }, { change(editor.copy(category = it)) }, enabled = enabled)
        when (editor.category) {
            QuestionCategory.GRAMMAR -> {
                Text("Bài ngữ pháp *", style = MaterialTheme.typography.labelLarge)
                if (state.lessons.isEmpty()) Text("Chưa có bài ngữ pháp. Hãy tạo bài trước.")
                ChipRow(state.lessons.map { it.id }, editor.lessonId, { state.lessonTitle(it).orEmpty() },
                    { change(editor.copy(lessonId = it)) }, enabled = enabled)
            }
            QuestionCategory.VOCABULARY -> OutlinedTextField(editor.topicText, { change(editor.copy(topicText = it.filter(Char::isDigit))) },
                Modifier.fillMaxWidth(), label = { Text("Mã chủ đề từ vựng *") }, singleLine = true, enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Tạm nhập mã số; sẽ chọn từ danh sách khi có bảng topics.") })
            QuestionCategory.PLACEMENT -> Text("Câu hỏi dùng cho bài kiểm tra trình độ đầu vào.", style = MaterialTheme.typography.bodySmall)
        }
        FieldError(errors[QuestionField.CATEGORY])

        Text("Kiểu trả lời *", style = MaterialTheme.typography.labelLarge)
        ChipRow(AnswerType.entries, editor.answerType, { it.label() }, { change(editor.copy(answerType = it)) }, enabled = enabled)

        OutlinedTextField(editor.content, { change(editor.copy(content = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Nội dung câu hỏi *") }, minLines = 2, enabled = enabled,
            placeholder = { Text("Ví dụ: She ___ to school every day.") },
            isError = QuestionField.CONTENT in errors, supportingText = errors[QuestionField.CONTENT]?.let { { Text(it) } })

        when (editor.answerType) {
            AnswerType.MULTIPLE_CHOICE -> {
                Text("4 đáp án · chọn 1 đáp án đúng *", style = MaterialTheme.typography.labelLarge)
                editor.choices.forEachIndexed { i, choice ->
                    Row(Modifier.fillMaxWidth().selectable(editor.correctIndex == i, enabled = enabled, role = Role.RadioButton,
                        onClick = { change(editor.copy(correctIndex = i)) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = editor.correctIndex == i, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(choice, { v -> change(editor.copy(choices = editor.choices.toMutableList().also { it[i] = v })) },
                            Modifier.weight(1f), label = { Text("Đáp án ${'A' + i}") }, singleLine = true, enabled = enabled,
                            isError = QuestionField.OPTIONS in errors && choice.isBlank())
                    }
                }
            }
            AnswerType.FILL_BLANK -> {
                Text("Đáp án chấp nhận * (không phân biệt hoa thường, bỏ khoảng trắng thừa)", style = MaterialTheme.typography.labelLarge)
                editor.accepted.forEachIndexed { i, answer ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(answer, { v -> change(editor.copy(accepted = editor.accepted.toMutableList().also { it[i] = v })) },
                            Modifier.weight(1f), label = { Text("Đáp án ${i + 1}") }, singleLine = true, enabled = enabled,
                            isError = QuestionField.OPTIONS in errors && answer.isBlank())
                        if (editor.accepted.size > 1) TextButton(onClick = {
                            change(editor.copy(accepted = editor.accepted.filterIndexed { j, _ -> j != i }))
                        }, enabled = enabled) { Text("Xoá") }
                    }
                }
                OutlinedButton(onClick = { change(editor.copy(accepted = editor.accepted + "")) }, enabled = enabled) { Text("+ Thêm đáp án") }
            }
        }
        FieldError(errors[QuestionField.OPTIONS])
        FieldError(errors[QuestionField.CORRECT])

        OutlinedTextField(editor.explanation, { change(editor.copy(explanation = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Giải thích") }, minLines = 2, enabled = enabled)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::save, enabled = enabled) { Text(if (state.busy) "Đang lưu…" else "Lưu") }
            OutlinedButton(onClick = viewModel::cancelEdit, enabled = enabled) { Text("Huỷ") }
        }
    }
}
