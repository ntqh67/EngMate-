package com.engmate.app.navigation

import com.engmate.app.feature.quiz.domain.QuizSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizRouteTest {
    @Test fun roundTripsEveryRoute() {
        listOf(
            QuizRoute.Quiz(QuizSource.Lesson(3)), QuizRoute.Quiz(QuizSource.Topic(5)), QuizRoute.Quiz(QuizSource.Placement),
            QuizRoute.Result(42), QuizRoute.Placement, QuizRoute.Reminder,
        ).forEach { assertEquals(it, QuizRoute.decode(it.encode())) }
    }

    @Test fun placementScreenAndPlacementQuizAreDifferentKeys() {
        assertEquals("quiz:lesson:3", QuizRoute.Quiz(QuizSource.Lesson(3)).encode())
        assertEquals(QuizRoute.Placement, QuizRoute.decode("placement"))
        assertEquals(QuizRoute.Quiz(QuizSource.Placement), QuizRoute.decode("quiz:placement"))
    }

    @Test fun unknownOrBrokenKeysOpenNothing() {
        listOf(null, "", "quiz:", "quiz:lesson:x", "quiz-result:", "quiz-result:abc", "lesson:3", "settings")
            .forEach { assertNull(it, QuizRoute.decode(it)) }
    }
}
