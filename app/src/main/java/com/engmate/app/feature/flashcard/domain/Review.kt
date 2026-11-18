package com.engmate.app.feature.flashcard.domain

import com.engmate.app.core.database.WordLearningStatus
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

data class ReviewState(
    val repetition: Int = 0,
    val intervalDays: Int = 1,
    val easeFactor: Double = Sm2.START_EASE,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long = 0,
    val status: WordLearningStatus = WordLearningStatus.NEW,
)

/** Simplified SM-2 (F04): [quality] 0–5, below 3 means the card was forgotten and starts over. */
object Sm2 {
    const val START_EASE = 2.5
    const val MIN_EASE = 1.3
    /** "Chưa thuộc" button. */
    const val NOT_KNOWN = 2
    /** "Đã thuộc" button. */
    const val KNOWN = 4
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun review(state: ReviewState, quality: Int, now: Long): ReviewState {
        require(quality in 0..5) { "quality must be 0–5" }
        val miss = 5 - quality
        val ease = max(MIN_EASE, state.easeFactor + 0.1 - miss * (0.08 + miss * 0.02))
        val repetition = if (quality < 3) 0 else state.repetition + 1
        val interval = when (repetition) {
            0, 1 -> 1
            2 -> 6
            else -> (state.intervalDays * ease).roundToInt()
        }
        return ReviewState(repetition, interval, ease, now, now + interval * DAY_MS,
            if (quality >= KNOWN) WordLearningStatus.MASTERED else WordLearningStatus.LEARNING)
    }
}

interface ReviewRepository {
    fun observe(uid: String, word: String): Flow<ReviewState?>
    /** Number of the user's words whose next review is due now. */
    fun observeDueCount(uid: String): Flow<Int>
    suspend fun dueWords(uid: String, limit: Int = 50): List<String>
    /** Applies one SM-2 review and returns the new state. Throws [com.engmate.app.feature.vocabulary.domain.WordMissing]. */
    suspend fun review(uid: String, word: String, quality: Int): ReviewState
}
