package com.engmate.app.feature.progress.presentation

import com.engmate.app.feature.progress.data.local.DailyStudyStatEntity
import com.engmate.app.feature.quiz.data.local.QuizAttemptEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressDisplayTest {
    private val today = LocalDate.parse("2026-11-25")

    @Test fun `monthly minutes and active days line up with the calendar`() {
        val stats = listOf(
            DailyStudyStatEntity("u", "2026-11-25", studySeconds = 600),
            DailyStudyStatEntity("u", "2026-11-23", studySeconds = 120),
            DailyStudyStatEntity("u", "2026-10-27", studySeconds = 300),
        )
        val s = calculateProgress(stats, 0, emptyList(), today)
        assertEquals(30, s.monthlyMinutes.size)
        assertEquals(10L, s.monthlyMinutes.last())
        assertEquals(5L, s.monthlyMinutes.first()) // 29 days before today
        assertEquals(listOf(false, false, false, false, true, false, true), s.activeDays7)
        assertEquals(s.dailyMinutes, s.chartMinutes)
        assertEquals(s.monthlyMinutes, s.copy(rangeDays = 30).chartMinutes)
    }

    @Test fun `empty only when nothing at all was recorded`() {
        assertTrue(calculateProgress(emptyList(), 0, emptyList(), today).isEmpty)
        val quizOnly = calculateProgress(emptyList(), 0, listOf(QuizAttemptEntity(userUid = "u", totalQuestions = 10, correctCount = 5, completedAt = 1)), today)
        assertFalse(quizOnly.isEmpty)
        assertEquals(1, quizOnly.quizCount)
    }
}
