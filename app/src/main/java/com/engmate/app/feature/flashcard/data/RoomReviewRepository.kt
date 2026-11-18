package com.engmate.app.feature.flashcard.data

import com.engmate.app.core.contracts.NoOpProgressRecorder
import com.engmate.app.core.contracts.ProgressRecorder
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.flashcard.data.local.FlashcardDao
import com.engmate.app.feature.flashcard.data.local.WordProgressEntity
import com.engmate.app.feature.flashcard.domain.ReviewRepository
import com.engmate.app.feature.flashcard.domain.ReviewState
import com.engmate.app.feature.flashcard.domain.Sm2
import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordMissing
import com.engmate.app.feature.vocabulary.domain.requireOwner
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomReviewRepository @Inject constructor(
    private val dao: FlashcardDao, private val words: VocabularyDao, private val session: UserSession, private val clock: Clock,
    private val progress: ProgressRecorder = NoOpProgressRecorder(),
) : ReviewRepository {
    private fun WordProgressEntity.toDomain() = ReviewState(repetition, intervalDays, easeFactor, lastReviewedAt, nextReviewAt, status)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(uid: String, word: String): Flow<ReviewState?> {
        session.requireOwner(uid)
        return words.observeWord(WordKey.of(word)).flatMapLatest { entry ->
            if (entry == null) flowOf(null) else dao.observeProgress(uid, entry.id).map { it?.toDomain() }
        }
    }

    // shortcut: "now" is read once when collection starts (FlashcardDao.observeDue contract); screens re-subscribe per session.
    override fun observeDueCount(uid: String): Flow<Int> {
        session.requireOwner(uid)
        return dao.observeDue(uid, clock.millis()).map { it.size }
    }

    override suspend fun dueWords(uid: String, limit: Int): List<String> {
        session.requireOwner(uid)
        return dao.observeDue(uid, clock.millis()).first().take(limit).mapNotNull { words.getWord(it.wordId)?.word }
    }

    override suspend fun review(uid: String, word: String, quality: Int): ReviewState {
        session.requireOwner(uid)
        val entry = words.findWord(WordKey.of(word)) ?: throw WordMissing()
        val current = dao.getProgress(uid, entry.id)
        val next = Sm2.review(current?.toDomain() ?: ReviewState(), quality, clock.millis())
        dao.saveProgress(WordProgressEntity(userUid = uid, wordId = entry.id, repetition = next.repetition, intervalDays = next.intervalDays,
            easeFactor = next.easeFactor, lastReviewedAt = next.lastReviewedAt, nextReviewAt = next.nextReviewAt, status = next.status))
        // The review is already saved; a failure while recording progress must not undo or fail it.
        runCatching {
            progress.onCardReviewed(uid, entry.word, quality >= Sm2.KNOWN,
                newlyMastered = next.status == com.engmate.app.core.database.WordLearningStatus.MASTERED && current?.status != com.engmate.app.core.database.WordLearningStatus.MASTERED,
                reviewedAt = clock.millis())
        }
        return next
    }
}
