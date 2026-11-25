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
import com.engmate.app.feature.profile.domain.ProfileRepository

data class ProgressState(val learned: Long = 0, val due: Int = 0, val streak: Int = 0, val xp: Long = 0, val dailyMinutes: List<Long> = List(7) { 0 }, val accuracy: Int? = null, val loading: Boolean = true, val error: Boolean = false,
    /** Minutes per day for the last 30 days, oldest first (display only). */
    val monthlyMinutes: List<Long> = List(30) { 0 },
    /** Whether anything was studied on each of the last 7 days, oldest first. */
    val activeDays7: List<Boolean> = List(7) { false },
    val quizCount: Int = 0,
    /** Daily goal from the profile, for the goal line on the chart. */
    val goalMinutes: Int? = null,
    /** 7 or 30: the range the chart shows. */
    val rangeDays: Int = 7,
) {
    /** Nothing recorded yet: show the empty state instead of zeros. */
    val isEmpty: Boolean get() = !loading && !error && xp == 0L && quizCount == 0 && monthlyMinutes.all { it == 0L } && learned == 0L
    val chartMinutes: List<Long> get() = if (rangeDays == 30) monthlyMinutes else dailyMinutes
}
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(session: UserSession, study: StudyDao, flashcards: FlashcardDao, quiz: QuizDao, clock: Clock,
    profiles: ProfileRepository) : ViewModel() {
    private val range = MutableStateFlow(7)
    /** Shows the last 7 or 30 days on the chart. */
    fun setRange(days: Int) { range.value = if (days == 30) 30 else 7 }

    private val base: Flow<ProgressState> = session.user.flatMapLatest { user ->
        if (user == null) flowOf(ProgressState(loading = false)) else {
            val today = LocalDate.now(clock.withZone(java.time.ZoneId.systemDefault()))
            val goal = profiles.observe(user.uid).map { it?.dailyGoalMinutes }.catch { emit(null) }
            combine(study.observeAllStats(user.uid), flashcards.observeDue(user.uid, clock.millis()), quiz.observeAttempts(user.uid), goal) { stats, due, attempts, g ->
                calculateProgress(stats, due.size, attempts, today).copy(goalMinutes = g)
            }.catch { emit(ProgressState(loading = false, error = true)) }
        }
    }

    val state: StateFlow<ProgressState> = combine(base, range) { s, r -> s.copy(rangeDays = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressState())
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
    val month = (29 downTo 0).map { offset -> stats.firstOrNull { it.studyDate == today.minusDays(offset.toLong()).toString() }?.studySeconds?.div(60) ?: 0 }
    val active = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) in dates }
    return ProgressState(stats.sumOf { it.wordsLearned }, due, streak, stats.sumOf { it.xpEarned }, week, if (total == 0L) null else ((correct * 100) / total).toInt(), loading = false,
        monthlyMinutes = month, activeDays7 = active, quizCount = attempts.size)
}
