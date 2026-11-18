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
}

/** Default until the progress module ships: records nothing. */
class NoOpProgressRecorder : ProgressRecorder {
    override suspend fun onQuizCompleted(uid: String, xpEarned: Int, correctCount: Int, totalQuestions: Int,
                                         isPlacement: Boolean, completedAt: Long) = Unit
}
