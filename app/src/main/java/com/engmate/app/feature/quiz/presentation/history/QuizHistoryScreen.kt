package com.engmate.app.feature.quiz.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.BuildConfig
import com.engmate.app.core.ui.components.*
import com.engmate.app.feature.quiz.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy")
private fun formatDate(millis: Long) = dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun QuizHistoryScreen(viewModel: QuizHistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    val detail = state.detail
    BackHandler(enabled = state.selectedId != null) { viewModel.close() }

    Scaffold(
        topBar = {
            BackTopBar(if (state.selectedId == null) "Lịch sử làm bài" else "Chi tiết lượt làm",
                onBack = if (state.selectedId != null) viewModel::close else onBack) {
                if (detail != null) TextButton(onClick = { viewModel.askDelete(detail.summary) }, enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xoá") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.user == null -> Text("Vui lòng đăng nhập để xem lịch sử làm bài.", Modifier.padding(24.dp))
                state.selectedId != null -> if (detail == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    else AttemptDetailContent(detail, state, viewModel)
                else -> AttemptList(state, viewModel)
            }
        }
    }
    state.confirmDelete?.let { a ->
        ConfirmDeleteDialog("Xoá lượt làm?", "Lượt \"${a.title}\" (${a.correctCount}/${a.totalQuestions}) và toàn bộ câu trả lời sẽ bị xoá khỏi lịch sử.",
            state.busy, viewModel::delete, viewModel::dismissDelete)
    }
}

@Composable
private fun AttemptList(state: QuizHistoryUiState, viewModel: QuizHistoryViewModel) {
    Column(Modifier.fillMaxSize()) {
        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = viewModel::createSample, enabled = !state.busy, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(if (state.busy) "Đang tạo…" else "Tạo lượt làm mẫu (debug)")
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.attempts.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Chưa có lượt làm bài nào.")
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.attempts, key = { it.id }) { a ->
                    Card(Modifier.fillMaxWidth().clickable { viewModel.open(a.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${a.correctCount}/${a.totalQuestions} câu đúng · +${a.xpEarned} XP", style = MaterialTheme.typography.bodyMedium)
                                Text(formatDate(a.completedAt), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.askDelete(a) }, enabled = !state.busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xoá") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptDetailContent(detail: AttemptDetail, state: QuizHistoryUiState, viewModel: QuizHistoryViewModel) {
    val s = detail.summary
    val numbers = detail.answers.withIndex().associate { it.value.questionId to it.index + 1 }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleLarge)
                Text("${s.correctCount}/${s.totalQuestions} câu đúng · +${s.xpEarned} XP · ${formatDate(s.completedAt)}")
                if (s.isPlacement) Text("Trình độ ước tính: ${QuizScoring.levelFromScore(s.correctCount.coerceIn(0, QuizScoring.PLACEMENT_QUESTION_COUNT))}",
                    color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(4.dp))
                val wrong = detail.answers.count { !it.isCorrect }
                ChipRow(AnswerFilter.entries, state.filter, { f ->
                    "${f.label} (${when (f) { AnswerFilter.ALL -> detail.answers.size; AnswerFilter.WRONG -> wrong; AnswerFilter.CORRECT -> detail.answers.size - wrong }})"
                }, viewModel::filter)
            }
        }
        if (state.visibleAnswers.isEmpty()) item { Text("Không có câu nào trong mục này.", Modifier.padding(vertical = 16.dp)) }
        itemsIndexed(state.visibleAnswers, key = { _, a -> a.questionId }) { _, a ->
            val color = if (a.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Câu ${numbers[a.questionId]} · ${if (a.isCorrect) "✓ Đúng" else "✗ Sai"}", color = color,
                        style = MaterialTheme.typography.labelLarge)
                    Text(a.question, style = MaterialTheme.typography.bodyLarge)
                    val verb = if (a.answerType == AnswerType.MULTIPLE_CHOICE) "Bạn chọn" else "Bạn gõ"
                    Text("$verb: ${a.given ?: "(bỏ trống)"}", color = color)
                    Text("Đáp án đúng: ${a.correctAnswers.joinToString(" / ")}")
                    a.explanation?.let { Text("Giải thích: $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
