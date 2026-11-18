package com.engmate.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.profile.domain.ProfileRepository
import com.engmate.app.feature.profile.domain.UserProfile
import com.engmate.app.feature.quiz.domain.AttemptSummary
import com.engmate.app.feature.quiz.domain.QuizAttemptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val loading: Boolean = true,
    val user: SessionUser? = null,
    val profile: UserProfile? = null,
    val attempts: List<AttemptSummary> = emptyList(),
    val lessons: List<GrammarLesson> = emptyList(),
    val failed: Boolean = false,
) {
    val totalXp: Int get() = attempts.sumOf { it.xpEarned }
    /** Share of correct answers across all attempts, null before the first attempt. */
    val accuracy: Int? get() {
        val total = attempts.sumOf { it.totalQuestions }
        return if (total == 0) null else attempts.sumOf { it.correctCount } * 100 / total
    }
    /** First lesson (by order) the learner has not practised yet, else the first lesson. */
    val nextLesson: GrammarLesson? get() {
        val done = attempts.filterNot { it.isPlacement }.map { it.title }.toSet()
        val ordered = lessons.sortedBy { it.orderIndex }
        return ordered.firstOrNull { it.title !in done } ?: ordered.firstOrNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    session: UserSession,
    profiles: ProfileRepository,
    attempts: QuizAttemptRepository,
    grammar: GrammarRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = session.user.flatMapLatest { user ->
        if (user == null) grammar.observeLessons().map { HomeUiState(loading = false, lessons = it) }
        else combine(profiles.observe(user.uid), attempts.observeHistory(user.uid), grammar.observeLessons()) { p, a, l ->
            HomeUiState(loading = false, user = user, profile = p, attempts = a, lessons = l)
        }
    }.catch { emit(HomeUiState(loading = false, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
