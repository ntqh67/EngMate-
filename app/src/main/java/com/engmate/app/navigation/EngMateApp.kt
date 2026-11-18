package com.engmate.app.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.R
import com.engmate.app.core.ui.components.SectionHeader
import com.engmate.app.feature.grammar.presentation.admin.GrammarAdminScreen
import com.engmate.app.feature.grammar.presentation.lesson.GrammarLessonScreen
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsScreen
import com.engmate.app.feature.home.presentation.HomeScreen
import com.engmate.app.feature.profile.presentation.ProfileScreen
import com.engmate.app.feature.quiz.presentation.history.QuizHistoryScreen
import com.engmate.app.feature.quiz.presentation.questionbank.QuestionBankScreen
import com.engmate.app.feature.quiz.presentation.history.QuizHistoryViewModel

/** Top-level destinations. Add a tab here when a feature ships; unfinished features stay hidden. */
enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("Trang chủ", R.drawable.ic_home),
    LESSONS("Ngữ pháp", R.drawable.ic_school),
    HISTORY("Lịch sử", R.drawable.ic_history),
    PROFILE("Hồ sơ", R.drawable.ic_person),
}

/** Full-screen destinations opened on top of a tab; encoded as a string so they survive process death. */
private sealed interface Detail {
    data class Lesson(val id: Long) : Detail
    data object GrammarAdmin : Detail
    data object QuestionBank : Detail

    fun encode(): String = when (this) { is Lesson -> "lesson:$id"; GrammarAdmin -> "grammar-admin"; QuestionBank -> "question-bank" }

    companion object {
        fun decode(value: String?): Detail? = when {
            value == null -> null
            value.startsWith("lesson:") -> value.removePrefix("lesson:").toLongOrNull()?.let(::Lesson)
            value == "grammar-admin" -> GrammarAdmin
            value == "question-bank" -> QuestionBank
            else -> null
        }
    }
}

@Composable
fun EngMateApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = Detail.decode(detailKey)
    fun open(d: Detail) { detailKey = d.encode() }
    fun close() { detailKey = null }

    // Back closes a detail first, then returns to Home; from Home the system handles it (leaves the app).
    BackHandler(enabled = detail == null && tab != Tab.HOME) { tab = Tab.HOME }

    val history: QuizHistoryViewModel = viewModel()

    AnimatedContent(detail, label = "detail",
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { current ->
        when (current) {
            // Registered before the screen, so a screen's own handler (e.g. leaving an edit form) takes precedence.
            is Detail.Lesson -> { BackHandler { close() }; GrammarLessonScreen(viewModel(), current.id, ::close) }
            Detail.GrammarAdmin -> { BackHandler { close() }; GrammarAdminScreen(viewModel(), onBack = ::close) }
            Detail.QuestionBank -> { BackHandler { close() }; QuestionBankScreen(viewModel(), onBack = ::close) }
            null -> TabShell(tab, onSelect = { tab = it }) {
                when (tab) {
                    Tab.HOME -> HomeScreen(viewModel(),
                        onOpenLesson = { open(Detail.Lesson(it)) },
                        onOpenLessons = { tab = Tab.LESSONS },
                        onOpenAttempt = { history.open(it); tab = Tab.HISTORY },
                        onOpenHistory = { history.close(); tab = Tab.HISTORY },
                        onOpenProfile = { tab = Tab.PROFILE })
                    Tab.LESSONS -> GrammarLessonsScreen(viewModel(), onOpenLesson = { open(Detail.Lesson(it)) })
                    Tab.HISTORY -> QuizHistoryScreen(history)
                    Tab.PROFILE -> ProfileScreen(viewModel()) {
                        ContentManagement(onGrammar = { open(Detail.GrammarAdmin) }, onQuestions = { open(Detail.QuestionBank) })
                    }
                }
            }
        }
    }
}

/** Navigation bar on phones, navigation rail from 600dp (tablets, landscape). */
@Composable
private fun TabShell(selected: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { t ->
                    NavigationRailItem(selected = t == selected, onClick = { onSelect(t) }, colors = NavigationRailItemDefaults.colors(selectedTextColor = MaterialTheme.colorScheme.primary),
                        icon = { Icon(painterResource(t.icon), null) }, label = { Text(t.label) })
                }
                Spacer(Modifier.weight(1f))
            }
            // The rail already pads for the start-side system bars and cutout.
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))) { content() }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(selected = t == selected, onClick = { onSelect(t) }, colors = NavigationBarItemDefaults.colors(selectedTextColor = MaterialTheme.colorScheme.primary),
                            icon = { Icon(painterResource(t.icon), null) },
                            label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            // Inner screens own the status-bar inset; we only reserve room for the bar and consume its inset.
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(WindowInsets.navigationBars)) { content() }
        }
    }
}

@Composable
private fun ColumnScope.ContentManagement(onGrammar: () -> Unit, onQuestions: () -> Unit) {
    SectionHeader("Quản lý nội dung", Modifier.padding(top = 8.dp))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column {
            ManageRow(R.drawable.ic_book, "Bài ngữ pháp", "Thêm, sửa nội dung và ví dụ của bài học", onGrammar)
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ManageRow(R.drawable.ic_assignment, "Ngân hàng câu hỏi", "Câu hỏi trắc nghiệm và điền từ cho bài tập", onQuestions)
        }
    }
}

@Composable
private fun ManageRow(@DrawableRes icon: Int, title: String, body: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
