package com.engmate.app.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.components.SectionHeader
import com.engmate.app.feature.quiz.domain.QuizSource
import com.engmate.app.feature.quiz.presentation.placement.PlacementScreen
import com.engmate.app.feature.quiz.presentation.quiz.QuizScreen
import com.engmate.app.feature.quiz.presentation.result.QuizResultScreen
import com.engmate.app.feature.reminder.presentation.ReminderSettingsScreen

/** Full-screen quiz, result, placement and reminder pages; encoded as a string so they survive process death. */
internal sealed interface QuizRoute {
    data class Quiz(val source: QuizSource) : QuizRoute
    data class Result(val attemptId: Long) : QuizRoute
    data object Placement : QuizRoute
    data object Reminder : QuizRoute

    fun encode(): String = when (this) {
        is Quiz -> "quiz:${source.encode()}"
        is Result -> "quiz-result:$attemptId"
        Placement -> "placement"
        Reminder -> "reminder-settings"
    }

    companion object {
        fun decode(value: String?): QuizRoute? = when {
            value == null -> null
            value.startsWith("quiz:") -> QuizSource.decode(value.removePrefix("quiz:"))?.let(::Quiz)
            value.startsWith("quiz-result:") -> value.removePrefix("quiz-result:").toLongOrNull()?.let(::Result)
            value == "placement" -> Placement
            value == "reminder-settings" -> Reminder
            else -> null
        }
    }
}

/** Shows [route]; [navigate] opens another route or closes with null. */
@Composable
internal fun QuizRouteHost(route: QuizRoute, navigate: (QuizRoute?) -> Unit, onOpenProfile: () -> Unit) {
    // Registered before the screen, so a screen's own handler (e.g. confirming exit mid-quiz) takes precedence.
    BackHandler { navigate(null) }
    when (route) {
        is QuizRoute.Quiz -> QuizScreen(route.source, onFinished = { navigate(QuizRoute.Result(it)) },
            onBack = { navigate(null) }, onOpenProfile = onOpenProfile)
        is QuizRoute.Result -> QuizResultScreen(route.attemptId, onRetry = { navigate(QuizRoute.Quiz(it)) },
            onBack = { navigate(null) })
        QuizRoute.Placement -> PlacementScreen(onFinished = { navigate(null) }, onBack = { navigate(null) },
            onOpenProfile = onOpenProfile)
        QuizRoute.Reminder -> ReminderSettingsScreen(onBack = { navigate(null) })
    }
}

/** "Học tập" group shown in Cài đặt: placement test and daily reminder. */
@Composable
internal fun LearningShortcuts(onPlacement: () -> Unit, onReminder: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Học tập")
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()) {
            Column {
                ShortcutRow(R.drawable.ic_school, "Kiểm tra trình độ", "15 câu hỏi để xác định trình độ A1–B2", onPlacement)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ShortcutRow(R.drawable.ic_notifications, "Nhắc học hằng ngày", "Chọn giờ nhận thông báo nhắc học", onReminder)
            }
        }
    }
}

@Composable
private fun ShortcutRow(@DrawableRes icon: Int, title: String, body: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
