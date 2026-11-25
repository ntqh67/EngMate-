package com.engmate.app.feature.progress.data

import com.engmate.app.core.contracts.ProgressRecorder
import com.engmate.app.core.database.ActivityType
import com.engmate.app.feature.progress.data.local.LearningActivityEntity
import com.engmate.app.feature.progress.data.local.StudyDao
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Turns what the learner does into learning_activity rows; StudyDao rebuilds daily_study_stat from them, and the
 * Tiến độ screen (streak, XP, learned words, minutes per day) reads those stats. Durations are estimates per action.
 */
class RoomProgressRecorder @Inject constructor(private val study: StudyDao) : ProgressRecorder {
    private fun day(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private suspend fun log(uid: String, id: String, type: ActivityType, source: String?, xp: Int, seconds: Int, at: Long,
                            learned: Int = 0, reviewed: Int = 0, quizzes: Int = 0) {
        study.record(LearningActivityEntity(id, uid, type, source, xp, seconds, at, day(at), learned, reviewed, quizzes))
    }

    override suspend fun onQuizCompleted(uid: String, xpEarned: Int, correctCount: Int, totalQuestions: Int, isPlacement: Boolean, completedAt: Long) {
        log(uid, "quiz-$uid-$completedAt", if (isPlacement) ActivityType.PLACEMENT else ActivityType.QUIZ, null, xpEarned.coerceAtLeast(0),
            (totalQuestions * SECONDS_PER_QUESTION).coerceAtLeast(0), completedAt, quizzes = 1)
    }

    override suspend fun onCardReviewed(uid: String, word: String, known: Boolean, newlyMastered: Boolean, reviewedAt: Long) {
        log(uid, "card-${UUID.randomUUID()}", ActivityType.FLASHCARD, word, if (known) 10 else 3, SECONDS_PER_CARD, reviewedAt,
            learned = if (newlyMastered) 1 else 0, reviewed = 1)
    }

    override suspend fun onLessonStudied(uid: String, lessonId: Long, at: Long) {
        val id = "lesson-$uid-$lessonId-${day(at)}"
        if (study.getActivity(uid, id) != null) return
        log(uid, id, ActivityType.GRAMMAR, lessonId.toString(), 5, SECONDS_PER_LESSON, at)
    }

    override suspend fun onPronunciationPracticed(uid: String, score: Int, at: Long) {
        log(uid, "speak-${UUID.randomUUID()}", ActivityType.GRAMMAR, "pronunciation", (score / 10).coerceIn(0, 10), SECONDS_PER_SPEAKING, at)
    }

    private companion object {
        const val SECONDS_PER_QUESTION = 30
        const val SECONDS_PER_CARD = 15
        const val SECONDS_PER_LESSON = 120
        const val SECONDS_PER_SPEAKING = 20
    }
}
