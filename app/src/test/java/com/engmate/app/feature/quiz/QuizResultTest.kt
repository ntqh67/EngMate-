package com.engmate.app.feature.quiz

import com.engmate.app.feature.quiz.domain.*
import com.engmate.app.feature.quiz.presentation.result.AnswerFilter
import com.engmate.app.feature.quiz.presentation.result.QuizResultUiState
import com.engmate.app.feature.quiz.presentation.result.QuizResultViewModel
import org.junit.Assert.*
import org.junit.Test

class QuizResultTest {
    private fun summary(isPlacement: Boolean = false, lessonId: Long? = null, topicId: Long? = null, correct: Int = 2, total: Int = 3) =
        AttemptSummary(1, "Bài", isPlacement, correct, total, correct * 10, 0, lessonId, topicId)

    private fun answer(id: Long, correct: Boolean) =
        AnsweredQuestion(id, "Q$id", AnswerType.FILL_BLANK, "x", listOf("goes", "go"), null, correct)

    @Test fun retryUsesTheSameSource() {
        assertEquals(QuizSource.Lesson(3), summary(lessonId = 3).retrySource())
        assertEquals(QuizSource.Topic(5), summary(topicId = 5).retrySource())
        assertEquals(QuizSource.Placement, summary(isPlacement = true).retrySource())
        assertNull(summary().retrySource())
    }

    @Test fun reviewStartsOnMistakesWhenThereAreAny() {
        val withWrong = AttemptDetail(summary(), listOf(answer(1, true), answer(2, false)))
        val allRight = AttemptDetail(summary(correct = 2, total = 2), listOf(answer(1, true), answer(2, true)))
        assertEquals(AnswerFilter.WRONG, QuizResultViewModel.defaultFilter(withWrong))
        assertEquals(AnswerFilter.ALL, QuizResultViewModel.defaultFilter(allRight))
    }

    @Test fun stateComputesPercentageAndFilters() {
        val detail = AttemptDetail(summary(lessonId = 3, correct = 2, total = 3), listOf(answer(1, true), answer(2, false), answer(3, true)))
        val state = QuizResultUiState(attemptId = 1, loading = false, detail = detail, filter = AnswerFilter.WRONG)
        assertEquals(67, state.percent)
        assertEquals(1, state.wrongCount)
        assertEquals(listOf(2L), state.visibleAnswers.map { it.questionId })
        assertEquals(listOf(1L, 3L), state.copy(filter = AnswerFilter.CORRECT).visibleAnswers.map { it.questionId })
        assertEquals(QuizSource.Lesson(3), state.retrySource)
        assertEquals(0, QuizResultUiState(detail = AttemptDetail(summary(correct = 0, total = 0), emptyList())).percent)
    }
}
