package com.engmate.app.feature.quiz.domain

import java.util.Locale

object QuizScoring {
    const val XP_PER_CORRECT = 10
    const val PLACEMENT_QUESTION_COUNT = 15

    fun collapseSpaces(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    /** Trim, collapse inner whitespace and ignore case. */
    fun normalize(value: String): String = collapseSpaces(value).lowercase(Locale.ROOT)

    fun isFillBlankCorrect(answer: String?, accepted: List<String>): Boolean {
        val typed = normalize(answer.orEmpty())
        return typed.isNotEmpty() && accepted.any { normalize(it) == typed }
    }

    fun grade(question: Question, selectedOptionId: Long?, answerText: String?): Boolean = when (question.answerType) {
        AnswerType.MULTIPLE_CHOICE -> question.options.any { it.id == selectedOptionId && it.isCorrect }
        AnswerType.FILL_BLANK -> isFillBlankCorrect(answerText, question.correctOptions.map { it.content })
    }

    fun xp(correctCount: Int): Int {
        require(correctCount >= 0) { "correctCount must be >= 0" }
        return XP_PER_CORRECT * correctCount
    }

    /** 0–4 A1, 5–8 A2, 9–12 B1, 13–15 B2. */
    fun levelFromScore(correctCount: Int): String {
        require(correctCount in 0..PLACEMENT_QUESTION_COUNT) { "Placement score must be 0..15" }
        return when (correctCount) {
            in 0..4 -> "A1"
            in 5..8 -> "A2"
            in 9..12 -> "B1"
            else -> "B2"
        }
    }
}
