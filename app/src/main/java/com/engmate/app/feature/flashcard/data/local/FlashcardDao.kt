package com.engmate.app.feature.flashcard.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FlashcardDao {
    @Insert protected abstract suspend fun insertProgress(progress: WordProgressEntity): Long
    @Update protected abstract suspend fun updateProgress(progress: WordProgressEntity): Int
    @Query("SELECT * FROM word_progress WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun getProgress(uid: String, wordId: Long): WordProgressEntity?
    @Query("SELECT * FROM word_progress WHERE user_uid = :uid AND next_review_at <= :now ORDER BY next_review_at, word_id")
    abstract fun observeDue(uid: String, now: Long): Flow<List<WordProgressEntity>>
    @Transaction
    open suspend fun saveProgress(progress: WordProgressEntity) {
        require(progress.repetition >= 0 && progress.intervalDays >= 0)
        require(progress.easeFactor.isFinite() && progress.easeFactor >= 1.3)
        require(progress.nextReviewAt >= 0 && (progress.lastReviewedAt == null || progress.lastReviewedAt >= 0))
        val existing = getProgress(progress.userUid, progress.wordId)
        if (existing == null) insertProgress(progress.copy(id = 0))
        else updateProgress(progress.copy(id = existing.id))
    }
    @Query("DELETE FROM word_progress WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun deleteProgress(uid: String, wordId: Long): Int

    @Insert abstract suspend fun insertCollection(collection: FlashcardCollectionEntity): Long
    @Query("SELECT * FROM flashcard_collections WHERE user_uid = :uid ORDER BY created_at DESC, id DESC")
    abstract fun observeCollections(uid: String): Flow<List<FlashcardCollectionEntity>>
    @Query("SELECT * FROM flashcard_collections WHERE user_uid = :uid AND id = :id")
    abstract suspend fun getCollection(uid: String, id: Long): FlashcardCollectionEntity?
    @Query("UPDATE flashcard_collections SET title = :title, description = :description WHERE user_uid = :uid AND id = :id")
    abstract suspend fun updateCollection(uid: String, id: Long, title: String, description: String?): Int
    @Query("DELETE FROM flashcard_collections WHERE user_uid = :uid AND id = :id")
    abstract suspend fun deleteCollection(uid: String, id: Long): Int

    @Insert protected abstract suspend fun insertCard(card: FlashcardEntity): Long
    @Transaction
    open suspend fun addCard(uid: String, card: FlashcardEntity): Long {
        requireNotNull(getCollection(uid, card.collectionId)) { "Collection does not belong to this user" }
        return insertCard(card.copy(id = 0))
    }
    @Query("SELECT f.* FROM flashcards f JOIN flashcard_collections c ON f.collection_id = c.id WHERE c.user_uid = :uid AND c.id = :collectionId ORDER BY f.id")
    abstract fun observeCards(uid: String, collectionId: Long): Flow<List<FlashcardEntity>>
    @Query("UPDATE flashcards SET word = :word, meaning = :meaning, example = :example, phonetic = :phonetic WHERE id = :id AND collection_id IN (SELECT id FROM flashcard_collections WHERE user_uid = :uid)")
    abstract suspend fun updateCard(uid: String, id: Long, word: String, meaning: String, example: String?, phonetic: String?): Int
    @Query("DELETE FROM flashcards WHERE id = :id AND collection_id IN (SELECT id FROM flashcard_collections WHERE user_uid = :uid)")
    abstract suspend fun deleteCard(uid: String, id: Long): Int
}
