package com.engmate.app.feature.progress.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.progress.data.local.StudyDao
import com.engmate.app.feature.flashcard.data.local.FlashcardDao
import com.engmate.app.feature.quiz.data.local.QuizDao
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class ProgressState(val learned: Long = 0, val due: Int = 0, val streak: Int = 0, val xp: Long = 0, val dailyMinutes: List<Long> = List(7) { 0 }, val accuracy: Int? = null, val loading: Boolean = true, val error: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(session: UserSession, study: StudyDao, flashcards: FlashcardDao, quiz: QuizDao, clock: Clock) : ViewModel() {
    val state: StateFlow<ProgressState> = session.user.flatMapLatest { user ->
        if (user == null) flowOf(ProgressState(loading = false)) else {
            val today = LocalDate.now(clock.withZone(java.time.ZoneId.systemDefault()))
            combine(study.observeAllStats(user.uid), flashcards.observeDue(user.uid, clock.millis()), quiz.observeAttempts(user.uid)) { stats, due, attempts ->
                calculateProgress(stats, due.size, attempts, today)
            }.catch { emit(ProgressState(loading = false, error = true)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressState())
}

internal fun calculateProgress(
    stats: List<com.engmate.app.feature.progress.data.local.DailyStudyStatEntity>,
    due: Int,
    attempts: List<com.engmate.app.feature.quiz.data.local.QuizAttemptEntity>,
    today: LocalDate,
): ProgressState {
    val dates = stats.mapNotNull { runCatching { LocalDate.parse(it.studyDate) }.getOrNull() }.filter { it <= today }.toSet()
    var day = if (today in dates) today else today.minusDays(1)
    var streak = 0
    while (day in dates) { streak++; day = day.minusDays(1) }
    val week = (6 downTo 0).map { offset -> stats.firstOrNull { it.studyDate == today.minusDays(offset.toLong()).toString() }?.studySeconds?.div(60) ?: 0 }
    val total = attempts.sumOf { it.totalQuestions.toLong() }
    val correct = attempts.sumOf { it.correctCount.toLong() }
    return ProgressState(stats.sumOf { it.wordsLearned }, due, streak, stats.sumOf { it.xpEarned }, week, if (total == 0L) null else ((correct * 100) / total).toInt(), loading = false)
}
