package com.engmate.app.feature.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.EmptyState
import com.engmate.app.core.ui.components.LevelBadge
import com.engmate.app.core.ui.components.SectionHeader
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.quiz.domain.AttemptSummary
import java.time.LocalTime

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "Chào buổi sáng"
    in 11..13 -> "Chào buổi trưa"
    in 14..17 -> "Chào buổi chiều"
    else -> "Chào buổi tối"
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenLesson: (Long) -> Unit,
    onOpenLessons: () -> Unit,
    onOpenAttempt: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 24.dp, bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { Greeting(state, onOpenProfile) }
            if (state.failed) item {
                Text("Không tải được dữ liệu học. Hãy mở lại ứng dụng.", Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.error)
            }
            if (state.profile != null) item { ProgressSummary(state) }

            item {
                SectionHeader("Tiếp tục học", action = if (state.lessons.isNotEmpty()) {
                    { TextButton(onClick = onOpenLessons) { Text("Tất cả bài") } }
                } else null)
            }
            item {
                val next = state.nextLesson
                if (next == null) EmptyState("Chưa có bài ngữ pháp", "Bài học sẽ xuất hiện ở đây khi được thêm vào.")
                else NextLessonCard(next) { onOpenLesson(next.id) }
            }

            if (state.user != null) {
                item {
                    SectionHeader("Kết quả gần đây", action = if (state.attempts.isNotEmpty()) {
                        { TextButton(onClick = onOpenHistory) { Text("Xem lịch sử") } }
                    } else null)
                }
                if (state.attempts.isEmpty()) item {
                    Text("Bạn chưa làm bài tập nào. Kết quả mỗi lượt làm sẽ được lưu tại đây.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else items(state.attempts.take(3), key = { it.id }) { attempt -> AttemptRow(attempt) { onOpenAttempt(attempt.id) } }
            }
        }
    }
}

@Composable
private fun Greeting(state: HomeUiState, onOpenProfile: () -> Unit) {
    val profile = state.profile
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (profile != null) "${greeting()}, ${profile.displayName.substringAfterLast(' ')}" else greeting(),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        when {
            state.user == null -> Text("Đăng nhập để lưu tiến độ học của bạn.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            profile == null -> {
                Text("Tạo hồ sơ để đặt trình độ và mục tiêu học mỗi ngày.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onOpenProfile, Modifier.padding(top = 4.dp)) { Text("Tạo hồ sơ") }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBadge(profile.englishLevel)
                Text("Mục tiêu ${profile.dailyGoalMinutes} phút mỗi ngày",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressSummary(state: HomeUiState) {
    Surface(Modifier.fillMaxWidth().padding(top = 20.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(vertical = 16.dp).height(IntrinsicSize.Min)) {
            Stat("Lượt làm", state.attempts.size.toString(), Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Stat("Tổng XP", state.totalXp.toString(), Modifier.weight(1f), accent = true)
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Stat("Độ chính xác", state.accuracy?.let { "$it%" } ?: "–", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, accent: Boolean = false) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NextLessonCard(lesson: GrammarLesson, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBadge(lesson.level)
                Text("Ngữ pháp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(lesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            lesson.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onClick, Modifier.padding(top = 4.dp)) { Text("Học bài này") }
        }
    }
}

@Composable
private fun AttemptRow(attempt: AttemptSummary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(attempt.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${attempt.correctCount}/${attempt.totalQuestions} câu đúng") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_bolt), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text("${attempt.xpEarned} XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        },
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
