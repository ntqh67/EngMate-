package com.engmate.app.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.feature.auth.presentation.*
import androidx.compose.ui.res.painterResource
import com.engmate.app.R
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.feature.grammar.presentation.lesson.GrammarLessonScreen
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsScreen
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsViewModel
import com.engmate.app.feature.flashcard.presentation.review.FlashcardScreen
import com.engmate.app.feature.home.presentation.HomeScreen
import com.engmate.app.feature.vocabulary.presentation.worddetail.WordDetailScreen
import com.engmate.app.feature.notebook.presentation.NotebookScreen
import com.engmate.app.feature.profile.presentation.*
import com.engmate.app.feature.progress.presentation.*
import com.engmate.app.feature.quiz.domain.QuizSource

/** Bottom bar, in design order. */
enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    HOME("Trang chủ", R.drawable.ic_home),
    LEARN("Học", R.drawable.ic_school),
    NOTEBOOK("Sổ tay", R.drawable.ic_book),
    PROFILE("Hồ sơ", R.drawable.ic_person),
}

@Composable fun EngMateApp(auth: AuthViewModel = viewModel(), profile: ProfileViewModel = viewModel(), progress: ProgressViewModel = viewModel()) {
    val user by auth.session.user.collectAsStateWithLifecycle()
    val profileState by profile.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(user?.uid) { mutableStateOf(MainTab.HOME) }
    // Open grammar lesson inside the Học tab; null shows the lesson list.
    var lessonId by rememberSaveable(user?.uid) { mutableStateOf<Long?>(null) }
    // Full-screen pages over the tabs: Chi tiết từ vựng, and a flashcard round for a handbook.
    var openWord by rememberSaveable(user?.uid) { mutableStateOf<String?>(null) }
    var studyHandbook by rememberSaveable(user?.uid) { mutableStateOf<Long?>(null) }
    // Đông's quiz, result, placement and reminder pages (see QuizRoutes.kt).
    var quizRouteKey by rememberSaveable(user?.uid) { mutableStateOf<String?>(null) }
    val quizRoute = QuizRoute.decode(quizRouteKey)
    fun openQuiz(route: QuizRoute?) { quizRouteKey = route?.encode() }
    // Guests can browse: home, dictionary search and grammar lessons. Personal features ask for a login.
    var showAuth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(user) { if (user != null) showAuth = false }
    val needsProfile = user != null && (tab == MainTab.NOTEBOOK || tab == MainTab.PROFILE || quizRoute != null || studyHandbook != null)
    if (showAuth && user == null) {
        BackHandler { showAuth = false }
        Box(Modifier.fillMaxSize()) {
            AuthScreen(auth)
            TextButton(onClick = { showAuth = false }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Text("Để sau") }
        }
    } else if (needsProfile && (profileState.user?.uid != user?.uid || profileState.loading)) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
    } else if (needsProfile && profileState.profile == null && !profileState.loadFailed) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) { ProfileScreen(profile) }
            Row(Modifier.padding(horizontal = 24.dp)) {
                TextButton(onClick = { tab = MainTab.HOME; quizRouteKey = null; studyHandbook = null }) { Text("Để sau") }
                TextButton(onClick = auth::signOut) { Text("Đăng xuất") }
            }
        }
    } else if (openWord != null) {
        BackHandler { openWord = null }
        WordDetailScreen(viewModel(), openWord!!, onBack = { openWord = null })
    } else if (studyHandbook != null) {
        BackHandler { studyHandbook = null }
        FlashcardScreen(viewModel(), studyHandbook!!, onClose = { studyHandbook = null })
    } else if (quizRoute != null) {
        QuizRouteHost(quizRoute, navigate = ::openQuiz, onOpenProfile = { openQuiz(null); tab = MainTab.PROFILE })
    } else {
        BackHandler(enabled = tab != MainTab.HOME || lessonId != null) {
            if (tab == MainTab.LEARN && lessonId != null) lessonId = null else tab = MainTab.HOME
        }
        val lessons: GrammarLessonsViewModel = viewModel()
        Scaffold(bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { if (t == MainTab.LEARN && tab == MainTab.LEARN) lessonId = null; tab = t },
                        icon = { Icon(painterResource(t.icon), null) }, label = { Text(t.label, maxLines = 1) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth()) {
                    when (tab) {
                        MainTab.HOME -> HomeScreen(viewModel(), onOpenLessons = { lessonId = null; tab = MainTab.LEARN },
                            onOpenLesson = { lessonId = it; tab = MainTab.LEARN }, onOpenWord = { openWord = it.word })
                        // Đông's grammar screens (restored from before 2d5cb51).
                        MainTab.LEARN -> lessonId?.let { GrammarLessonScreen(lessons, it, onBack = { lessonId = null },
                            onPractice = { id -> if (user == null) showAuth = true else openQuiz(QuizRoute.Quiz(QuizSource.Lesson(id))) }) }
                            ?: GrammarLessonsScreen(lessons, onOpenLesson = { lessonId = it })
                        MainTab.NOTEBOOK -> if (user == null) LoginPrompt("Sổ tay từ vựng", onLogin = { showAuth = true }) else NotebookScreen(viewModel(), onOpenWord = { openWord = it }, onStudy = { studyHandbook = it })
                        MainTab.PROFILE -> if (user == null) LoginPrompt("Hồ sơ và tiến độ học", onLogin = { showAuth = true }) else ProfileTab(profile, progress, user?.email.orEmpty(), auth::signOut,
                            onPlacement = { openQuiz(QuizRoute.Placement) }, onReminder = { openQuiz(QuizRoute.Reminder) })
                    }
                }
            }
        }
    }
}

/** Hồ sơ holds the profile and Huân's statistics; ⚙ opens settings with sign-out. */
@Composable private fun ProfileTab(profile: ProfileViewModel, progress: ProgressViewModel, email: String, onSignOut: () -> Unit,
    onPlacement: () -> Unit = {}, onReminder: () -> Unit = {}) {
    var showProgress by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    if (settings) {
        BackHandler { settings = false }
        Column {
            BackTopBar("Cài đặt", onBack = { settings = false })
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LearningShortcuts(onPlacement, onReminder)
                Text(email)
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (showProgress) 1 else 0, Modifier.statusBarsPadding()) {
            Tab(selected = !showProgress, onClick = { showProgress = false }, text = { Text("Hồ sơ") })
            Tab(selected = showProgress, onClick = { showProgress = true }, text = { Text("Tiến độ") })
        }
        Box(Modifier.weight(1f)) {
            if (showProgress) ProgressScreen(progress) else ProfileScreen(profile, onSettings = { settings = true })
        }
    }
}

/** Shown in place of a personal tab while browsing as a guest. */
@Composable private fun LoginPrompt(feature: String, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(feature, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Đăng nhập để lưu dữ liệu học của bạn và đồng bộ giữa các thiết bị.", style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin) { Text("Đăng nhập / Đăng ký") }
    }
}
