package com.engmate.app.feature.home

import com.engmate.app.core.database.ActivityType
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.home.presentation.HomeUiState
import com.engmate.app.feature.home.presentation.buildHomeState
import com.engmate.app.feature.home.presentation.recentAfterOpen
import com.engmate.app.feature.profile.domain.UserProfile
import com.engmate.app.feature.progress.data.local.DailyStudyStatEntity
import com.engmate.app.feature.progress.data.local.LearningActivityEntity
import com.engmate.app.feature.quiz.domain.AttemptSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    private val today = LocalDate.of(2026, 11, 25)
    private fun lesson(id: Long, title: String, level: String = "A1", order: Int = id.toInt()) =
        GrammarLesson(id, title, level, null, "", emptyList(), order, null)
    private fun attempt(id: Long, lessonId: Long?, correct: Int, total: Int = 10, at: Long = id, title: String = "Bài $lessonId") =
        AttemptSummary(id, title, false, correct, total, correct * 10, at, lessonId = lessonId)
    private fun profile(level: String = "A1", goal: Int = 15) = UserProfile("u", "u@x.vn", "Eastern", null, level, goal, 0, 0)
    private fun stat(date: LocalDate, seconds: Long = 0, xp: Long = 0) = DailyStudyStatEntity("u", date.toString(), xpEarned = xp, studySeconds = seconds)
    private fun grammar(lessonId: String?, at: Long) = LearningActivityEntity("a$at", "u", ActivityType.GRAMMAR, lessonId, 5, 120, at, today.toString())

    @Test fun `next lesson matches practised lessons by id, not by title`() {
        // Two lessons share a title; practising lesson 1 must not hide lesson 2.
        val state = HomeUiState(lessons = listOf(lesson(1, "Thì hiện tại"), lesson(2, "Thì hiện tại")), attempts = listOf(attempt(1, 1, 8)))
        assertEquals(2L, state.nextLesson?.id)
    }

    @Test fun `continue lesson is the last opened lesson, else the next one`() {
        val lessons = listOf(lesson(1, "A"), lesson(2, "B"), lesson(3, "C"))
        val state = buildHomeState(profile(), lessons, emptyList(), emptyList(), listOf(grammar("3", 10), grammar("2", 20), grammar("pronunciation", 30)), 0, today)
        assertEquals(2L, state.continueLesson?.id)
        assertEquals(1L, state.copy(lastLessonId = 99).continueLesson?.id)
    }

    @Test fun `best percent is the highest score of a lesson`() {
        val state = HomeUiState(attempts = listOf(attempt(1, 5, 6), attempt(2, 5, 9), attempt(3, 6, 10)))
        assertEquals(90, state.bestPercent(5))
        assertNull(state.bestPercent(7))
    }

    @Test fun `today minutes, streak and xp come from daily stats`() {
        val stats = listOf(stat(today, seconds = 600, xp = 40), stat(today.minusDays(1), seconds = 60, xp = 10), stat(today.minusDays(3), xp = 5))
        val state = buildHomeState(profile(goal = 15), emptyList(), emptyList(), stats, emptyList(), 4, today)
        assertEquals(10, state.todayMinutes)
        assertEquals(2, state.streak)
        assertEquals(55L, state.totalXp)
        assertEquals(4, state.dueCount)
        assertFalse(state.goalReached)
        assertTrue(state.copy(todayMinutes = 15).goalReached)
    }

    @Test fun `suggestions are unpractised lessons at the profile level`() {
        val lessons = listOf(lesson(1, "A", "A2"), lesson(2, "B", "A2"), lesson(3, "C", "B1"), lesson(4, "D", "A2"), lesson(5, "E", "A2"), lesson(6, "F", "A2"))
        val state = buildHomeState(profile(level = "A2"), lessons, listOf(attempt(1, 2, 5)), emptyList(), emptyList(), null, today)
        // Lesson 1 is "Tiếp tục" (first unpractised), lesson 2 was practised, lesson 3 is another level.
        assertEquals(listOf(4L, 5L, 6L), state.suggestions.map { it.id })
    }

    @Test fun `first run only when nothing was learned yet`() {
        assertTrue(buildHomeState(profile(), emptyList(), emptyList(), emptyList(), emptyList(), 0, today).isFirstRun)
        assertFalse(buildHomeState(profile(), emptyList(), emptyList(), listOf(stat(today)), emptyList(), 0, today).isFirstRun)
        assertFalse(HomeUiState(signedIn = false).isFirstRun)
    }

    @Test fun `recent shows the three newest attempts`() {
        val state = HomeUiState(attempts = (1L..5L).map { attempt(it, it, 5, at = it * 100) })
        assertEquals(listOf(5L, 4L, 3L), state.recent.map { it.id })
    }

    @Test fun `recent searches put the opened word first without duplicates`() {
        assertEquals(listOf("travel", "apple"), recentAfterOpen(listOf("apple", "Travel"), "travel"))
        assertEquals(8, recentAfterOpen((1..8).map { "w$it" }, "new").size)
    }
}
