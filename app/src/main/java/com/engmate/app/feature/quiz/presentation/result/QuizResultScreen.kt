package com.engmate.app.feature.quiz.presentation.result

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.*
import com.engmate.app.feature.quiz.domain.QuizSource
import kotlin.random.Random

/** Result of a saved attempt with the answer review. [onRetry] starts a new quiz from the same source. */
@Composable
fun QuizResultScreen(attemptId: Long, onRetry: (QuizSource) -> Unit, onBack: () -> Unit) {
    val viewModel: QuizResultViewModel = viewModel(key = "quiz-result:$attemptId")
    LaunchedEffect(attemptId) { viewModel.load(attemptId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    QuizResultContent(state, title = null, onBack = onBack, onFilter = viewModel::filter, onReload = viewModel::retry, streak = streak) {
        val source = state.retrySource
        if (source != null) ButtonPair("Làm lại", { onRetry(source) }, "Xong", onBack)
        else PrimaryButton("Xong", onBack, Modifier.fillMaxWidth())
    }
}

/** Shared by QuizResultScreen and the placement "Xem chi tiết" step. [actions] sit in the sticky bottom bar. */
@Composable
internal fun QuizResultContent(state: QuizResultUiState, title: String?, onBack: () -> Unit, onFilter: (AnswerFilter) -> Unit,
                               onReload: () -> Unit, streak: Int? = null, actions: @Composable ColumnScope.() -> Unit) {
    val detail = state.detail
    val ready = !state.loading && !state.signedOut && state.error == null && !state.missing && detail != null
    Scaffold(
        topBar = { AppTopBar(title, onBack, navigationIcon = R.drawable.ic_close, navigationLabel = "Đóng") },
        bottomBar = { if (ready) StickyBottomBar(content = actions) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState(Modifier.padding(screenPadding), rows = 3)
                state.signedOut -> EmptyState("Bạn cần đăng nhập để xem kết quả", icon = R.drawable.ic_lock, action = { SecondaryButton("Quay lại", onBack) })
                state.error != null -> ErrorState(state.error, onRetry = onReload)
                state.missing || detail == null -> EmptyState("Lượt làm không còn tồn tại", "Có thể lượt làm đã bị xoá khỏi lịch sử.",
                    icon = R.drawable.ic_history, action = { SecondaryButton("Quay lại", onBack) })
                else -> {
                    val numbers = detail.answers.withIndex().associate { it.value.questionId to it.index + 1 }
                    val s = detail.summary
                    LazyColumn(contentPadding = PaddingValues(horizontal = screenPadding, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        item(key = "score") {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                val good = state.percent >= 80
                                ProgressRing(state.percent / 100f, size = 148.dp, strokeWidth = 12.dp,
                                    color = if (good) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.primary) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${s.correctCount}/${s.totalQuestions}", style = EngMateTheme.type.stat)
                                        Text("câu đúng", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(resultMessage(state.percent), style = MaterialTheme.typography.headlineSmall)
                                Text(s.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                        item(key = "tiles") {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                StatCard("+${s.xpEarned}", "XP nhận được", R.drawable.ic_bolt, Modifier.weight(1f), tint = MaterialTheme.colorScheme.tertiary)
                                StatCard("${state.percent}%", "Độ chính xác", R.drawable.ic_target, Modifier.weight(1f))
                                if (streak != null && streak > 0) StatCard("$streak ngày", "Chuỗi ngày học", R.drawable.ic_flame, Modifier.weight(1f),
                                    tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        item(key = "reviewH") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                SectionHeader("Xem lại câu trả lời")
                                AnswerFilterRow(detail.answers.size, state.wrongCount, state.filter, onFilter)
                            }
                        }
                        if (state.visibleAnswers.isEmpty()) item(key = "none") {
                            Text("Không có câu nào trong mục này.", Modifier.padding(vertical = Spacing.l), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(state.visibleAnswers, key = { it.questionId }) { a -> AnswerReviewCard(numbers[a.questionId] ?: 0, a) }
                    }
                    if (state.percent >= 80) Celebration()
                }
            }
        }
    }
}

/** Short confetti burst for a score of 80% or more; skipped when the system removes animations. */
@Composable
private fun Celebration() {
    if (rememberReduceMotion()) return
    val colors = listOf(EngMateTheme.brand.accent, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, EngMateTheme.brand.brand)
    val pieces = remember { List(36) { Triple(Random.nextFloat(), Random.nextFloat() * 0.4f + 0.6f, Random.nextFloat() * 360f) } }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1200, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        pieces.forEachIndexed { i, (x, speed, angle) ->
            val y = (t.value * speed * 1.2f - 0.1f) * size.height
            val alpha = (1f - t.value).coerceIn(0f, 1f)
            rotate(angle + t.value * 360f, Offset(x * size.width, y)) {
                drawRect(colors[i % colors.size].copy(alpha = alpha), Offset(x * size.width, y), Size(14f, 8f))
            }
        }
    }
}

