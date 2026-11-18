package com.engmate.app.feature.flashcard

import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.feature.flashcard.domain.ReviewState
import com.engmate.app.feature.flashcard.domain.Sm2
import org.junit.Assert.*
import org.junit.Test

class Sm2Test {
    private val day = 24 * 60 * 60 * 1000L

    @Test fun knownCardsGetLongerIntervals() {
        var state = ReviewState()
        val intervals = (1..4).map { state = Sm2.review(state, Sm2.KNOWN, now = it * day); state.intervalDays }
        assertEquals(listOf(1, 6, 15, 38), intervals)
        assertEquals(WordLearningStatus.MASTERED, state.status)
        assertEquals(4 * day + 38 * day, state.nextReviewAt)
        assertEquals(2.5, state.easeFactor, 1e-9) // quality 4 keeps the ease factor
    }

    @Test fun forgettingRestartsAndLowersEase() {
        val learned = Sm2.review(Sm2.review(ReviewState(), 5, 0), 5, 0)
        val forgot = Sm2.review(learned, Sm2.NOT_KNOWN, now = 10 * day)
        assertEquals(0, forgot.repetition)
        assertEquals(1, forgot.intervalDays)
        assertEquals(11 * day, forgot.nextReviewAt)
        assertEquals(WordLearningStatus.LEARNING, forgot.status)
        assertTrue(forgot.easeFactor < learned.easeFactor)
    }

    @Test fun easeNeverDropsBelowMinimum() {
        var state = ReviewState()
        repeat(20) { state = Sm2.review(state, 0, 0) }
        assertEquals(Sm2.MIN_EASE, state.easeFactor, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class) fun qualityOutOfRangeIsRejected() { Sm2.review(ReviewState(), 6, 0) }
}
