package com.engmate.app.feature.quiz

import com.engmate.app.feature.quiz.domain.*
import org.junit.Assert.*
import org.junit.Test

class QuizScoringTest {
    @Test fun levelFromScoreBoundaries() {
        mapOf(0 to "A1", 4 to "A1", 5 to "A2", 8 to "A2", 9 to "B1", 12 to "B1", 13 to "B2", 15 to "B2")
            .forEach { (score, level) -> assertEquals("score $score", level, QuizScoring.levelFromScore(score)) }
    }

    @Test(expected = IllegalArgumentException::class) fun levelRejectsNegative() { QuizScoring.levelFromScore(-1) }
    @Test(expected = IllegalArgumentException::class) fun levelRejectsAboveFifteen() { QuizScoring.levelFromScore(16) }

    @Test fun xpIsTenPerCorrectAnswer() {
        assertEquals(0, QuizScoring.xp(0))
        assertEquals(70, QuizScoring.xp(7))
    }

    @Test fun fillBlankIgnoresCaseAndExtraSpaces() {
        assertTrue(QuizScoring.isFillBlankCorrect(" Goes ", listOf("goes")))
        assertTrue(QuizScoring.isFillBlankCorrect("ARE   not  watching", listOf("are not watching", "aren't watching")))
        assertTrue(QuizScoring.isFillBlankCorrect("aren't watching", listOf("are not watching", "aren't watching")))
        assertFalse(QuizScoring.isFillBlankCorrect("go", listOf("goes")))
        assertFalse(QuizScoring.isFillBlankCorrect("   ", listOf("goes")))
        assertFalse(QuizScoring.isFillBlankCorrect(null, listOf("goes")))
    }

    @Test fun gradeMultipleChoiceUsesSelectedOption() {
        val q = Question(1, null, 1, false, AnswerType.MULTIPLE_CHOICE, "She ___ to school.", null,
            listOf(QuestionOption(10, "go", false, 0), QuestionOption(11, "goes", true, 1),
                QuestionOption(12, "going", false, 2), QuestionOption(13, "gone", false, 3)), null)
        assertTrue(QuizScoring.grade(q, 11, null))
        assertFalse(QuizScoring.grade(q, 10, null))
        assertFalse(QuizScoring.grade(q, null, "goes"))
    }

    @Test fun gradeFillBlankUsesAcceptedAnswers() {
        val q = Question(2, null, 1, false, AnswerType.FILL_BLANK, "He ___ (watch) TV.", null,
            listOf(QuestionOption(20, "watches", true, 0)), null)
        assertTrue(QuizScoring.grade(q, null, "  Watches "))
        assertFalse(QuizScoring.grade(q, null, "watch"))
    }
}
