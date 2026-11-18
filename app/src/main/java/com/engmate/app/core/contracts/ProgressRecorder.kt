package com.engmate.app.core.contracts

/**
 * Records learning progress after a quiz. The quiz module calls it; the progress module implements it.
 * Bound in di/IntegrationContractsModule; the default is [NoOpProgressRecorder].
 */
interface ProgressRecorder {
    /**
     * Called once after a quiz attempt has been saved. Huân implements it: add XP, record the day's activity,
     * update the streak. The attempt is already stored, so a failure here does not lose the result.
     */
    suspend fun onQuizCompleted(uid: String, xpEarned: Int, correctCount: Int, totalQuestions: Int, isPlacement: Boolean, completedAt: Long)

    /** One flashcard review. [newlyMastered] is true the first time the word reaches MASTERED (counts as a learned word). */
    suspend fun onCardReviewed(uid: String, word: String, known: Boolean, newlyMastered: Boolean, reviewedAt: Long) = Unit

    /** A grammar lesson was opened; counted once per lesson per day. */
    suspend fun onLessonStudied(uid: String, lessonId: Long, at: Long) = Unit

    /** A pronunciation attempt with a score of 0–100. */
    suspend fun onPronunciationPracticed(uid: String, score: Int, at: Long) = Unit
}

/** Default until the progress module ships: records nothing. */
class NoOpProgressRecorder : ProgressRecorder {
    override suspend fun onQuizCompleted(uid: String, xpEarned: Int, correctCount: Int, totalQuestions: Int,
                                         isPlacement: Boolean, completedAt: Long) = Unit
}
