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
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.grammar.presentation.lesson.GrammarLessonScreen
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsScreen
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsViewModel
import com.engmate.app.feature.flashcard.presentation.review.FlashcardScreen
import com.engmate.app.feature.flashcard.presentation.review.FlashcardViewModel
import com.engmate.app.feature.home.presentation.HomeActions
import com.engmate.app.feature.home.presentation.HomeScreen
import com.engmate.app.feature.home.presentation.SearchScreen
import com.engmate.app.feature.vocabulary.presentation.worddetail.WordDetailScreen
import com.engmate.app.feature.vocabulary.presentation.topics.VocabularyTopicsScreen
import com.engmate.app.feature.notebook.presentation.NotebookScreen
import com.engmate.app.feature.profile.presentation.*
import com.engmate.app.feature.progress.presentation.*
import com.engmate.app.feature.quiz.domain.QuizSource

/** Bottom bar, in design order. */
enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    HOME("Trang chủ", R.drawable.ic_home),
    LEARN("Học", R.drawable.ic_school),
    NOTEBOOK("Sổ tay", R.drawable.ic_notebook),
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
    // Ôn tập hôm nay: a flashcard round over every word due for review.
    var studyDue by rememberSaveable(user?.uid) { mutableStateOf(false) }
    // Tra từ, opened from the home header; a word opened from it returns here.
    var searching by rememberSaveable { mutableStateOf(false) }
    // Cài đặt and Sửa hồ sơ open as full pages from the Hồ sơ tab.
    var settingsOpen by rememberSaveable(user?.uid) { mutableStateOf(false) }
    var editProfile by rememberSaveable(user?.uid) { mutableStateOf(false) }
    // Đông's quiz, result, placement and reminder pages (see QuizRoutes.kt).
    var quizRouteKey by rememberSaveable(user?.uid) { mutableStateOf<String?>(null) }
    val quizRoute = QuizRoute.decode(quizRouteKey)
    fun openQuiz(route: QuizRoute?) { quizRouteKey = route?.encode() }
    // Guests can browse: home, dictionary search and grammar lessons. Personal features ask for a login.
    val actions: ProgressActionsViewModel = viewModel()
    LaunchedEffect(lessonId, user?.uid) { if (user != null) lessonId?.let(actions::lessonOpened) }
    var learnVocabulary by rememberSaveable { mutableStateOf(false) }
    var showAuth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(user) { if (user != null) showAuth = false }
    val needsProfile = user != null && (tab == MainTab.NOTEBOOK || tab == MainTab.PROFILE || quizRoute != null || studyHandbook != null || studyDue)
    if (showAuth && user == null) {
        BackHandler { showAuth = false }
        Box(Modifier.fillMaxSize()) {
            AuthScreen(auth)
            TextButton(onClick = { showAuth = false }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Text("Để sau") }
        }
    } else if (needsProfile && (profileState.user?.uid != user?.uid || profileState.loading)) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
    } else if (needsProfile && profileState.profile == null && !profileState.loadFailed) {
        ProfileScreen(profile, onLater = { tab = MainTab.HOME; quizRouteKey = null; studyHandbook = null; studyDue = false }, onSignOut = auth::signOut)
    } else if (openWord != null) {
        BackHandler { openWord = null }
        WordDetailScreen(viewModel(), openWord!!, onBack = { openWord = null }, onPronunciationScored = actions::pronunciationScored)
    } else if (searching) {
        BackHandler { searching = false }
        SearchScreen(viewModel(), onBack = { searching = false }, onOpenWord = { openWord = it.word })
    } else if (studyHandbook != null) {
        BackHandler { studyHandbook = null }
        FlashcardScreen(viewModel(), studyHandbook!!, onClose = { studyHandbook = null }, onFindWords = { studyHandbook = null; searching = true })
    } else if (studyDue) {
        BackHandler { studyDue = false }
        FlashcardScreen(viewModel(key = "flashcard-due"), FlashcardViewModel.DUE, onClose = { studyDue = false })
    } else if (quizRoute != null) {
        QuizRouteHost(quizRoute, navigate = ::openQuiz, onOpenProfile = { openQuiz(null); tab = MainTab.PROFILE })
    } else if (editProfile) {
        BackHandler { profile.cancelEdit(); editProfile = false }
        // Closing after a successful save: the form leaves editing mode once the profile is stored.
        ProfileScreen(profile, onClose = { profile.cancelEdit(); editProfile = false }, onPlacement = { openQuiz(QuizRoute.Placement) })
        // Saving or cancelling ends editing; leave the page then, unless the save failed validation.
        var wasEditing by remember { mutableStateOf(false) }
        LaunchedEffect(profileState.editing, profileState.busy) {
            if (wasEditing && !profileState.editing && !profileState.busy) editProfile = false
            wasEditing = profileState.editing || (wasEditing && profileState.busy)
        }
    } else if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(profile, user?.email.orEmpty(), onBack = { settingsOpen = false }, onPlacement = { openQuiz(QuizRoute.Placement) },
            onReminder = { openQuiz(QuizRoute.Reminder) }, onEditProfile = { editProfile = true }, onSignOut = { settingsOpen = false; auth.signOut() })
    } else if (lessonId != null) {
        // A lesson is a full page: no bottom bar, its own sticky "Làm bài tập" button.
        BackHandler { lessonId = null }
        val lessons: GrammarLessonsViewModel = viewModel()
        GrammarLessonScreen(lessons, lessonId!!, onBack = { lessonId = null },
            onPractice = { id -> if (user == null) showAuth = true else openQuiz(QuizRoute.Quiz(QuizSource.Lesson(id))) })
    } else {
        BackHandler(enabled = tab != MainTab.HOME) { tab = MainTab.HOME }
        val lessons: GrammarLessonsViewModel = viewModel()
        Scaffold(bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { t ->
                    // Selected icon and label share one color.
                    NavigationBarItem(selected = tab == t, onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), null) }, label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth()) {
                    when (tab) {
                        MainTab.HOME -> HomeScreen(viewModel(), HomeActions(
                            onOpenLessons = { learnVocabulary = false; tab = MainTab.LEARN },
                            onOpenLesson = { lessonId = it },
                            onOpenSearch = { searching = true },
                            onOpenVocabulary = { learnVocabulary = true; tab = MainTab.LEARN },
                            onReviewDue = { studyDue = true },
                            onOpenNotebook = { tab = MainTab.NOTEBOOK },
                            onPlacement = { openQuiz(QuizRoute.Placement) },
                            onOpenAttempt = { openQuiz(QuizRoute.Result(it)) },
                            onOpenProfile = { tab = MainTab.PROFILE },
                            onLogin = { showAuth = true },
                        ))
                        // One level of choice: the Học heading and a segmented switch, no second tab row.
                        MainTab.LEARN -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                            Text("Học", style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = screenPadding).padding(top = Spacing.l, bottom = Spacing.m))
                            AppSegmentedTabs(listOf("Ngữ pháp", "Từ vựng"), if (learnVocabulary) 1 else 0, { learnVocabulary = it == 1 },
                                Modifier.padding(horizontal = screenPadding))
                            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                                if (learnVocabulary) VocabularyTopicsScreen(viewModel(), onOpenWord = { openWord = it })
                                else GrammarLessonsScreen(lessons, onOpenLesson = { lessonId = it })
                            }
                        }
                        MainTab.NOTEBOOK -> if (user == null) LoginPrompt("Sổ tay", onLogin = { showAuth = true }) else NotebookScreen(viewModel(), onOpenWord = { openWord = it }, onStudy = { studyHandbook = it },
                            onFindWords = { searching = true }, onBrowseTopics = { learnVocabulary = true; tab = MainTab.LEARN })
                        MainTab.PROFILE -> if (user == null) LoginPrompt("Hồ sơ và tiến độ học", onLogin = { showAuth = true }, icon = R.drawable.ic_chart) else ProfileHomeScreen(profile, progress, onEdit = { editProfile = true }, onSettings = { settingsOpen = true },
                            onStudy = { learnVocabulary = false; tab = MainTab.LEARN }, onReviewDue = { studyDue = true })
                    }
                }
            }
        }
    }
}

/** Shown in place of a personal tab while browsing as a guest. */
@Composable private fun LoginPrompt(feature: String, onLogin: () -> Unit, @DrawableRes icon: Int = R.drawable.ic_notebook) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        EmptyState(feature, "Đăng nhập để lưu từ, tiến độ và chuỗi ngày học, đồng bộ giữa các thiết bị.", icon = icon,
            action = { PrimaryButton("Đăng nhập hoặc đăng ký", onLogin) })
    }
}
