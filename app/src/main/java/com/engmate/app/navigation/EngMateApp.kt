package com.engmate.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.feature.auth.presentation.*
import androidx.compose.ui.res.painterResource
import com.engmate.app.R
import com.engmate.app.feature.grammar.presentation.lesson.GrammarLessonScreen
import com.engmate.app.feature.grammar.presentation.topics.*
import com.engmate.app.feature.home.presentation.*
import com.engmate.app.feature.quiz.presentation.history.*
import com.engmate.app.feature.profile.presentation.*
import com.engmate.app.feature.progress.presentation.*

@Composable fun EngMateApp(auth: AuthViewModel = viewModel(), profile: ProfileViewModel = viewModel(), progress: ProgressViewModel = viewModel()) {
    val user by auth.session.user.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    var tab by remember(user?.uid) { mutableStateOf(0) }
    if (user == null) AuthScreen(auth) else if (profileState.user?.uid != user?.uid || profileState.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
    } else if (profileState.profile == null && !profileState.loadFailed) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) { ProfileScreen(profile) }
            TextButton(onClick = auth::signOut, modifier = Modifier.padding(horizontal = 24.dp)) { Text("Đăng xuất") }
        }
    } else {
        var lessonId by remember { mutableStateOf<Long?>(null) }
        var history by remember { mutableStateOf(false) }
        val home: HomeViewModel = viewModel()
        val lessons: GrammarLessonsViewModel = viewModel()
        val quizHistory: QuizHistoryViewModel = viewModel()
        BackHandler(enabled = tab != 0 || lessonId != null || history) {
            when { history -> history = false; lessonId != null -> lessonId = null; else -> tab = 0 }
        }
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("Trang chủ" to R.drawable.ic_home, "Học" to R.drawable.ic_book, "Tiến độ" to R.drawable.ic_chart,
                    "Hồ sơ" to R.drawable.ic_person, "Cài đặt" to R.drawable.ic_settings).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i; lessonId = null; history = false },
                        icon = { Icon(painterResource(icon), null) }, label = { Text(label, maxLines = 1) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth()) {
                when(tab) {
                    0 -> if (history) QuizHistoryScreen(quizHistory, onBack = { history = false }) else HomeScreen(home,
                        onOpenLesson = { lessonId = it; tab = 1 }, onOpenLessons = { tab = 1 },
                        onOpenAttempt = { history = true }, onOpenHistory = { history = true }, onOpenProfile = { tab = 3 })
                    1 -> lessonId?.let { GrammarLessonScreen(lessons, it, onBack = { lessonId = null }) }
                        ?: GrammarLessonsScreen(lessons, onOpenLesson = { lessonId = it })
                    2 -> ProgressScreen(progress)
                    3 -> ProfileScreen(profile, onSettings = { tab = 4 })
                    else -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
                        Text(user?.email.orEmpty())
                        OutlinedButton(onClick = auth::signOut, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
                    }
                }
                }
            }
        }
    }
}
