package com.engmate.app.feature.quiz

import com.engmate.app.feature.quiz.domain.QuizSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizSourceTest {
    @Test fun encodesToStableStrings() {
        assertEquals("lesson:3", QuizSource.Lesson(3).encode())
        assertEquals("topic:5", QuizSource.Topic(5).encode())
        assertEquals("placement", QuizSource.Placement.encode())
    }

    @Test fun roundTripsEverySource() {
        listOf(QuizSource.Lesson(3), QuizSource.Topic(5), QuizSource.Placement, QuizSource.Lesson(Long.MAX_VALUE))
            .forEach { assertEquals(it, QuizSource.decode(it.encode())) }
    }

    @Test fun rejectsUnknownOrBrokenValues() {
        listOf(null, "", "lesson:", "lesson:abc", "topic:1.5", "placement:1", "quiz:lesson:3", "Lesson:3")
            .forEach { assertNull(it, QuizSource.decode(it)) }
    }
}
