package com.engmate.app.feature.grammar.presentation.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.profile.domain.ProfileRepository
import com.engmate.app.feature.quiz.domain.AttemptSummary
import com.engmate.app.feature.quiz.domain.QuestionRepository
import com.engmate.app.feature.quiz.domain.QuizAttemptRepository
import com.engmate.app.feature.quiz.presentation.quiz.QuizViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class GrammarLessonsUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val lessons: List<GrammarLesson> = emptyList(),
    val level: String? = null,
    val query: String = "",
    /** Best quiz score per lesson in percent; empty for guests. */
    val bestPercent: Map<Long, Int> = emptyMap(),
    /** The learner's level from the profile, to mark lessons that fit. */
    val profileLevel: String? = null,
) {
    val levels: List<String> get() = lessons.map { it.level }.distinct().sorted()
    val visible: List<GrammarLesson> get() = lessons.filter { level == null || it.level == level }
        .filter { query.isBlank() || it.title.contains(query.trim(), true) || it.summary.orEmpty().contains(query.trim(), true) }.sortedBy { it.orderIndex }
    fun lesson(id: Long) = lessons.firstOrNull { it.id == id }
    /** Lessons with at least one attempt. */
    val doneCount: Int get() = lessons.count { it.id in bestPercent }
}

/** Best score in percent per lesson over [attempts]; attempts without a lesson or questions are skipped. */
internal fun bestPercentByLesson(attempts: List<AttemptSummary>): Map<Long, Int> = attempts
    .filter { it.lessonId != null && it.totalQuestions > 0 }
    .groupBy { it.lessonId!! }
    .mapValues { (_, list) -> list.maxOf { Math.round(it.correctCount * 100f / it.totalQuestions) } }

/** Read-only lesson browsing for learners. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GrammarLessonsViewModel @Inject constructor(
    repository: GrammarRepository,
    session: UserSession,
    attempts: QuizAttemptRepository,
    profiles: ProfileRepository,
    private val questions: QuestionRepository,
) : ViewModel() {
    private val level = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    // Personal data is optional: a guest or a failed read just shows lessons without status.
    private val personal: Flow<Pair<Map<Long, Int>, String?>> = session.user.flatMapLatest { user ->
        if (user == null) flowOf(emptyMap<Long, Int>() to null)
        else combine(attempts.observeHistory(user.uid).map(::bestPercentByLesson), profiles.observe(user.uid).map { it?.englishLevel }) { b, l -> b to l }
            .catch { emit(emptyMap<Long, Int>() to null) }
    }

    val state: StateFlow<GrammarLessonsUiState> = combine(repository.observeLessons(), level, query, personal) { lessons, level, query, (best, profileLevel) ->
        GrammarLessonsUiState(loading = false, lessons = lessons, level = level, query = query, bestPercent = best, profileLevel = profileLevel)
    }.catch { emit(GrammarLessonsUiState(loading = false, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarLessonsUiState())

    fun search(text: String) { query.value = text }
    fun filter(level: String?) { this.level.value = level }

    /** Number of questions the lesson's quiz will have (at most one quiz round); 0 when it has none or reading fails. */
    suspend fun questionCount(lessonId: Long): Int =
        runCatching { questions.questionsForLesson(lessonId).size.coerceAtMost(QuizViewModel.QUIZ_SIZE) }.getOrDefault(0)
}
