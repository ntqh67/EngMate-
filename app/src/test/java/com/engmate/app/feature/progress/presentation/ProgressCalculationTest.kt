package com.engmate.app.feature.progress.presentation

import com.engmate.app.feature.progress.data.local.DailyStudyStatEntity
import com.engmate.app.feature.quiz.data.local.QuizAttemptEntity
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressCalculationTest {
    private val today = LocalDate.parse("2026-11-18")
    @Test fun aggregatesHistoryAndWeightsQuizAccuracyByQuestions() {
        val stats = listOf(
            DailyStudyStatEntity("u", "2026-11-17", xpEarned = 20, studySeconds = 120, wordsLearned = 3),
            DailyStudyStatEntity("u", "2026-11-16", xpEarned = 10, studySeconds = 60, wordsLearned = 2)
        )
        val attempts = listOf(QuizAttemptEntity(userUid = "u", totalQuestions = 2, correctCount = 2, completedAt = 1), QuizAttemptEntity(userUid = "u", totalQuestions = 8, correctCount = 4, completedAt = 2))
        val result = calculateProgress(stats, 4, attempts, today)
        assertEquals(5L, result.learned); assertEquals(30L, result.xp); assertEquals(2, result.streak)
        assertEquals(4, result.due); assertEquals(60, result.accuracy)
        assertEquals(listOf(0L, 0L, 0L, 0L, 1L, 2L, 0L), result.dailyMinutes)
    }
    @Test fun noHistoryDoesNotInventAccuracyOrStreak() {
        val result = calculateProgress(emptyList(), 0, emptyList(), today)
        assertNull(result.accuracy); assertEquals(0, result.streak)
    }
}
