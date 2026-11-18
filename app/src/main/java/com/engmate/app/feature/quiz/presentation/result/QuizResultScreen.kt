package com.engmate.app.feature.quiz.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.EmptyState
import com.engmate.app.feature.quiz.domain.QuizSource
import com.engmate.app.feature.quiz.presentation.history.AnswerFilter

/** Result of a saved attempt with the answer review. [onRetry] starts a new quiz from the same source. */
@Composable
fun QuizResultScreen(attemptId: Long, onRetry: (QuizSource) -> Unit, onBack: () -> Unit) {
    val viewModel: QuizResultViewModel = viewModel(key = "quiz-result:$attemptId")
    LaunchedEffect(attemptId) { viewModel.load(attemptId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuizResultContent(state, title = "Kết quả", onBack = onBack, onFilter = viewModel::filter, onReload = viewModel::retry) {
        val source = state.retrySource
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (source != null) OutlinedButton(onClick = { onRetry(source) }, modifier = Modifier.weight(1f)) { Text("Làm lại") }
            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Xong") }
        }
    }
}

/** Shared by QuizResultScreen and the placement "Xem chi tiết" step. [actions] sit under the score. */
@Composable
internal fun QuizResultContent(state: QuizResultUiState, title: String, onBack: () -> Unit, onFilter: (AnswerFilter) -> Unit,
                               onReload: () -> Unit, actions: @Composable () -> Unit) {
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detail = state.detail
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.signedOut -> EmptyState("Bạn cần đăng nhập để xem kết quả", action = { OutlinedButton(onClick = onBack) { Text("Quay lại") } })
                state.error != null -> EmptyState(state.error, action = { Button(onClick = onReload) { Text("Thử lại") } })
                state.missing || detail == null -> EmptyState("Lượt làm không còn tồn tại", "Có thể lượt làm đã bị xoá khỏi lịch sử.",
                    action = { OutlinedButton(onClick = onBack) { Text("Quay lại") } })
                else -> {
                    val numbers = detail.answers.withIndex().associate { it.value.questionId to it.index + 1 }
                    val s = detail.summary
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                                        Stat("${s.correctCount}/${s.totalQuestions}", "Câu đúng")
                                        Stat("${state.percent}%", "Tỉ lệ đúng")
                                        Stat("+${s.xpEarned}", "XP", color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                actions()
                                Text("Xem lại câu trả lời", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                                AnswerFilterRow(detail.answers.size, state.wrongCount, state.filter, onFilter)
                            }
                        }
                        if (state.visibleAnswers.isEmpty()) item { Text("Không có câu nào trong mục này.", Modifier.padding(vertical = 16.dp)) }
                        items(state.visibleAnswers, key = { it.questionId }) { a -> AnswerReviewCard(numbers[a.questionId] ?: 0, a) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
