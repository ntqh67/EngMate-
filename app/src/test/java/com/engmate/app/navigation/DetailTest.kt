package com.engmate.app.navigation

import com.engmate.app.feature.quiz.domain.QuizSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailTest {
    @Test fun roundTripsEveryDetail() {
        listOf(
            Detail.Lesson(3), Detail.GrammarAdmin, Detail.QuestionBank,
            Detail.Quiz(QuizSource.Lesson(3)), Detail.Quiz(QuizSource.Topic(5)), Detail.Quiz(QuizSource.Placement),
            Detail.QuizResult(42), Detail.Placement, Detail.ReminderSettings,
        ).forEach { assertEquals(it, Detail.decode(it.encode())) }
    }

    @Test fun existingKeysStillOpenTheSameScreens() {
        assertEquals(Detail.Lesson(3), Detail.decode("lesson:3"))
        assertEquals(Detail.GrammarAdmin, Detail.decode("grammar-admin"))
        assertEquals(Detail.QuestionBank, Detail.decode("question-bank"))
    }

    @Test fun quizKeysDoNotCollideWithLessonKeys() {
        assertEquals("quiz:lesson:3", Detail.Quiz(QuizSource.Lesson(3)).encode())
        assertEquals(Detail.Quiz(QuizSource.Lesson(3)), Detail.decode("quiz:lesson:3"))
        assertEquals(Detail.Lesson(3), Detail.decode("lesson:3"))
        // Detail.Placement opens the placement screen; a placement quiz is a different key.
        assertEquals(Detail.Placement, Detail.decode("placement"))
        assertEquals(Detail.Quiz(QuizSource.Placement), Detail.decode("quiz:placement"))
    }

    @Test fun unknownOrBrokenKeysOpenNothing() {
        listOf(null, "", "quiz:", "quiz:lesson:x", "quiz-result:", "quiz-result:abc", "lesson:", "settings")
            .forEach { assertNull(it, Detail.decode(it)) }
    }
}
