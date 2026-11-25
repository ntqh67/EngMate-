package com.engmate.app.feature.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.core.ui.theme.success
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.quiz.domain.AttemptSummary
import com.engmate.app.feature.vocabulary.domain.Word
import java.time.LocalTime

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "Chào buổi sáng"
    in 11..12 -> "Chào buổi trưa"
    in 13..17 -> "Chào buổi chiều"
    else -> "Chào buổi tối"
}

/** Where the dashboard can send the learner; every target already exists elsewhere in the app. */
data class HomeActions(
    val onOpenLessons: () -> Unit = {},
    val onOpenLesson: (Long) -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onOpenVocabulary: () -> Unit = {},
    val onReviewDue: () -> Unit = {},
    val onOpenNotebook: () -> Unit = {},
    val onPlacement: () -> Unit = {},
    val onOpenAttempt: (Long) -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onLogin: () -> Unit = {},
)

@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: HomeActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, actions)
}

@Composable
internal fun HomeContent(state: HomeUiState, actions: HomeActions) {
    val pad = screenPadding
    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(start = pad, end = pad, top = Spacing.l, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        item(key = "header") { Header(state, actions) }
        when {
            state.failed -> item(key = "error") { ErrorState("Không tải được dữ liệu học. Hãy mở lại ứng dụng.") }
            state.loading -> item(key = "loading") { LoadingState(rows = 3) }
            else -> {
                item(key = "hero") { Hero(state, actions) }
                if (state.signedIn && !state.isFirstRun) item(key = "now") { DoNow(state, actions) }
                if (!state.signedIn) state.nextLesson?.let { lesson -> item(key = "next") { ContinueCard(lesson, null, actions, Modifier.fillMaxWidth()) } }
                item(key = "shortcuts") { Shortcuts(state, actions) }
                if (state.suggestions.isNotEmpty()) {
                    item(key = "sugH") { SectionHeader("Gợi ý cho trình độ ${state.profile?.englishLevel}", actionLabel = "Xem tất cả", onAction = actions.onOpenLessons) }
                    item(key = "sug") {
                        SurfaceGroup {
                            state.suggestions.forEachIndexed { i, lesson ->
                                if (i > 0) GroupDivider(72.dp)
                                ListRow(lesson.title, supporting = lesson.summary, onClick = { actions.onOpenLesson(lesson.id) },
                                    leading = { LevelChip(lesson.level, large = true) }, trailing = { StatusTag(ItemStatus.NEW, "Mới") })
                            }
                        }
                    }
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "recentH") { SectionHeader("Hoạt động gần đây") }
                    item(key = "recent") {
                        SurfaceGroup {
                            state.recent.forEachIndexed { i, attempt ->
                                if (i > 0) GroupDivider(72.dp)
                                RecentRow(attempt) { actions.onOpenAttempt(attempt.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, actions: HomeActions) {
    Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${greeting()},", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.profile?.displayName ?: "bạn", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        FilledTonalIconButton(onClick = actions.onOpenSearch, modifier = Modifier.size(Spacing.touch).testTag("home_search"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
            Icon(painterResource(R.drawable.ic_search), "Tra từ")
        }
    }
}

@Composable
private fun Hero(state: HomeUiState, actions: HomeActions) {
    when {
        !state.signedIn -> HeroCard {
            Text("Học thử miễn phí", style = MaterialTheme.typography.headlineSmall)
            Text("Đăng nhập để lưu tiến độ, chuỗi ngày học và sổ tay của bạn.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Đăng nhập", actions.onLogin)
        }
        state.isFirstRun -> HeroCard {
            Text("Chào mừng đến EngMate", style = MaterialTheme.typography.headlineSmall)
            Text("Làm bài kiểm tra 15 câu để EngMate gợi ý bài vừa sức, hoặc bắt đầu ngay với bài đầu tiên.",
                style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Làm bài kiểm tra trình độ", actions.onPlacement, Modifier.fillMaxWidth())
            state.nextLesson?.let { lesson ->
                OutlinedButton(onClick = { actions.onOpenLesson(lesson.id) }, Modifier.fillMaxWidth().heightIn(min = Spacing.button),
                    shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.outlinedButtonColors(contentColor = EngMateTheme.brand.onBrand)) {
                    Text("Bắt đầu bài đầu tiên")

                }
            }
        }
        else -> GoalHero(state, actions)
    }
}

@Composable
private fun GoalHero(state: HomeUiState, actions: HomeActions) {
    val goal = state.goalMinutes
    val onBrand = EngMateTheme.brand.onBrand
    HeroCard(onClick = state.continueLesson?.let { lesson -> { actions.onOpenLesson(lesson.id) } }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            val progress = if (goal == null) 0f else state.todayMinutes / goal.toFloat()
            ProgressRing(progress, size = 84.dp, color = if (state.goalReached) MaterialTheme.colorScheme.success else onBrand,
                trackColor = onBrand.copy(alpha = 0.16f)) {
                Text(if (goal == null) "${state.todayMinutes}'" else "${state.todayMinutes}/$goal'", style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Mục tiêu hôm nay", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        goal == null -> "Đặt mục tiêu mỗi ngày trong Hồ sơ"
                        state.goalReached -> "Đã đạt mục tiêu hôm nay"
                        state.todayMinutes == 0 -> "Bắt đầu $goal phút học hôm nay"
                        else -> "Còn ${goal - state.todayMinutes} phút nữa"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (state.streak > 0) StreakChip(state.streak)
            XpChip(state.totalXp)
            Spacer(Modifier.weight(1f))
            // The hero's one action: continue learning (same target as tapping the card).
            state.continueLesson?.let { lesson ->
                Button(onClick = { actions.onOpenLesson(lesson.id) }, modifier = Modifier.heightIn(min = Spacing.touch),
                    colors = ButtonDefaults.buttonColors(containerColor = onBrand, contentColor = EngMateTheme.brand.brand)) {
                    Text(if (state.goalReached) "Học thêm" else "Học ngay", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DoNow(state: HomeUiState, actions: HomeActions) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        val due = state.dueCount
        if (due != null) InfoCard(Modifier.weight(1f).fillMaxHeight(), onClick = if (due > 0) actions.onReviewDue else actions.onOpenNotebook) {
            Icon(painterResource(R.drawable.ic_layers), null, tint = MaterialTheme.colorScheme.secondary)
            if (due > 0) {
                Text("$due từ cần ôn", style = MaterialTheme.typography.titleMedium)
                Text("Đến hạn hôm nay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Ôn ngay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Không có từ đến hạn", style = MaterialTheme.typography.titleMedium)
                Text("Thêm từ vào Sổ tay để ôn theo lịch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Mở Sổ tay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        state.continueLesson?.let { lesson -> ContinueCard(lesson, state.bestPercent(lesson.id), actions, Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
private fun ContinueCard(lesson: GrammarLesson, best: Int?, actions: HomeActions, modifier: Modifier) {
    InfoCard(modifier, onClick = { actions.onOpenLesson(lesson.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(painterResource(R.drawable.ic_play), null, tint = MaterialTheme.colorScheme.primary)
            Text("Tiếp tục", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LevelChip(lesson.level)
        }
        Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (best != null) LinearProgressWithLabel(best / 100f, "Điểm tốt nhất", trailing = "$best%")
        else Text("Chưa làm bài tập", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Shortcuts(state: HomeUiState, actions: HomeActions) {
    val c = MaterialTheme.colorScheme
    val personal = { action: () -> Unit -> if (state.signedIn) action else actions.onLogin }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionHeader("Học nhanh")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ShortcutTile(R.drawable.ic_layers, "Ôn tập", personal(if ((state.dueCount ?: 0) > 0) actions.onReviewDue else actions.onOpenNotebook), Modifier.weight(1f), tint = c.secondary)
            ShortcutTile(R.drawable.ic_assignment, "Ngữ pháp", actions.onOpenLessons, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ShortcutTile(R.drawable.ic_book, "Từ vựng", actions.onOpenVocabulary, Modifier.weight(1f), tint = c.tertiary)
            ShortcutTile(R.drawable.ic_target, "Kiểm tra trình độ", personal(actions.onPlacement), Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentRow(attempt: AttemptSummary, onClick: () -> Unit) {
    val good = attempt.totalQuestions > 0 && attempt.correctCount * 100 >= attempt.totalQuestions * 80
    ListRow(attempt.title, supporting = "${attempt.correctCount}/${attempt.totalQuestions} câu đúng", onClick = onClick, headlineLines = 1,
        leading = {
            Icon(painterResource(if (good) R.drawable.ic_trophy else R.drawable.ic_assignment), null,
                Modifier.size(44.dp).padding(Spacing.s), tint = if (good) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        },
        trailing = { if (attempt.xpEarned > 0) XpChip(attempt.xpEarned.toLong(), gain = true) })
}
