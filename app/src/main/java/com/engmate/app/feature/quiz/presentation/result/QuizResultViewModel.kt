package com.engmate.app.feature.quiz.presentation.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.quiz.domain.*
import com.engmate.app.feature.quiz.presentation.history.AnswerFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QuizResultUiState(
    val attemptId: Long? = null,
    val loading: Boolean = true,
    val detail: AttemptDetail? = null,
    /** The attempt was deleted or belongs to someone else. */
    val missing: Boolean = false,
    val signedOut: Boolean = false,
    val error: String? = null,
    val filter: AnswerFilter = AnswerFilter.ALL,
) {
    val wrongCount: Int get() = detail?.answers?.count { !it.isCorrect } ?: 0
    val percent: Int get() = detail?.summary?.let { if (it.totalQuestions == 0) 0 else Math.round(it.correctCount * 100f / it.totalQuestions) } ?: 0
    val visibleAnswers: List<AnsweredQuestion> get() = detail?.answers.orEmpty().filter {
        when (filter) { AnswerFilter.ALL -> true; AnswerFilter.WRONG -> !it.isCorrect; AnswerFilter.CORRECT -> it.isCorrect }
    }
    val retrySource: QuizSource? get() = detail?.summary?.retrySource()
}

@HiltViewModel
class QuizResultViewModel @Inject constructor(
    private val attempts: QuizAttemptRepository,
    private val session: UserSession,
) : ViewModel() {
    private val mutable = MutableStateFlow(QuizResultUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun load(attemptId: Long) {
        if (mutable.value.attemptId == attemptId && mutable.value.error == null) return
        job?.cancel()
        mutable.value = QuizResultUiState(attemptId = attemptId)
        val uid = session.user.value?.uid
        if (uid == null) { mutable.value = QuizResultUiState(attemptId = attemptId, loading = false, signedOut = true); return }
        job = viewModelScope.launch {
            try {
                var first = true
                attempts.observeAttempt(uid, attemptId).collect { detail ->
                    mutable.update { s ->
                        if (detail == null) s.copy(loading = false, detail = null, missing = true)
                        else s.copy(loading = false, detail = detail, missing = false,
                            // Start on the mistakes when there are any; later updates keep the learner's choice.
                            filter = if (first) defaultFilter(detail) else s.filter)
                    }
                    first = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AttemptSessionRequired) { mutable.update { it.copy(loading = false, signedOut = true) } }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = "Không đọc được kết quả. Hãy thử lại.") } }
        }
    }

    fun retry() { mutable.value.attemptId?.let { mutable.update { s -> s.copy(error = null, attemptId = null) }; load(it) } }
    fun filter(filter: AnswerFilter) = mutable.update { it.copy(filter = filter) }

    companion object {
        fun defaultFilter(detail: AttemptDetail): AnswerFilter =
            if (detail.answers.any { !it.isCorrect }) AnswerFilter.WRONG else AnswerFilter.ALL
    }
}
