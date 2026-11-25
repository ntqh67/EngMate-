package com.engmate.app.feature.flashcard.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CollectionWithCounts(
    @Embedded val collection: FlashcardCollectionEntity,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "known_count") val knownCount: Int,
)

/** A card joined with its word's live data, the owner's note and progress. */
data class CardWithWordData(
    @Embedded val card: FlashcardEntity,
    @ColumnInfo(name = "meaning_vi") val meaningVi: String?,
    @ColumnInfo(name = "custom_meaning") val customMeaning: String?,
    val status: com.engmate.app.core.database.WordLearningStatus?,
)

@Dao
abstract class FlashcardDao {
    // Cards store the word text; words.word is NOCASE-unique, so the join finds the shared word row.
    @Query("""SELECT c.*, COUNT(f.id) AS word_count, COUNT(CASE WHEN p.status = 'MASTERED' THEN 1 END) AS known_count
              FROM flashcard_collections c
              LEFT JOIN flashcards f ON f.collection_id = c.id
              LEFT JOIN words w ON w.word = f.word COLLATE NOCASE
              LEFT JOIN word_progress p ON p.word_id = w.id AND p.user_id = c.user_id
              WHERE c.user_id = :uid GROUP BY c.id ORDER BY c.created_at DESC, c.id DESC""")
    abstract fun observeCollectionsWithCounts(uid: String): Flow<List<CollectionWithCounts>>

    @Query("""SELECT f.*, w.meaning_vi, n.custom_meaning, p.status
              FROM flashcards f
              JOIN flashcard_collections c ON c.id = f.collection_id
              LEFT JOIN words w ON w.word = f.word COLLATE NOCASE
              LEFT JOIN word_notes n ON n.word_id = w.id AND n.user_uid = c.user_id
              LEFT JOIN word_progress p ON p.word_id = w.id AND p.user_id = c.user_id
              WHERE c.user_id = :uid AND c.id = :collectionId ORDER BY f.id""")
    abstract fun observeCardsWithWordData(uid: String, collectionId: Long): Flow<List<CardWithWordData>>

    @Query("""SELECT f.collection_id FROM flashcards f JOIN flashcard_collections c ON c.id = f.collection_id
              WHERE c.user_id = :uid AND f.word = :word COLLATE NOCASE""")
    abstract fun observeCollectionIdsWithWord(uid: String, word: String): Flow<List<Long>>

    @Query("SELECT * FROM word_progress WHERE user_id = :uid AND word_id = :wordId")
    abstract fun observeProgress(uid: String, wordId: Long): Flow<WordProgressEntity?>

    @Query("SELECT * FROM flashcard_collections WHERE user_id = :uid")
    abstract suspend fun collectionsOf(uid: String): List<FlashcardCollectionEntity>

    @Query("SELECT * FROM flashcards WHERE collection_id = :collectionId AND word = :word COLLATE NOCASE")
    abstract suspend fun cardsWithWord(collectionId: Long, word: String): List<FlashcardEntity>

    @Insert protected abstract suspend fun insertProgress(progress: WordProgressEntity): Long
    @Update protected abstract suspend fun updateProgress(progress: WordProgressEntity): Int
    @Query("SELECT * FROM word_progress WHERE user_id = :uid AND word_id = :wordId")
    abstract suspend fun getProgress(uid: String, wordId: Long): WordProgressEntity?
    @Query("SELECT * FROM word_progress WHERE user_id = :uid AND next_review_at <= :now ORDER BY next_review_at, word_id")
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
    @Query("DELETE FROM word_progress WHERE user_id = :uid AND word_id = :wordId")
    abstract suspend fun deleteProgress(uid: String, wordId: Long): Int

    @Query("SELECT * FROM word_progress WHERE user_id = :uid")
    abstract fun observeAllProgress(uid: String): Flow<List<WordProgressEntity>>
    @Query("SELECT f.* FROM flashcards f JOIN flashcard_collections c ON c.id = f.collection_id WHERE c.user_id = :uid ORDER BY f.id")
    abstract fun observeAllCards(uid: String): Flow<List<FlashcardEntity>>

    @Insert abstract suspend fun insertCollection(collection: FlashcardCollectionEntity): Long
    @Query("SELECT * FROM flashcard_collections WHERE user_id = :uid ORDER BY created_at DESC, id DESC")
    abstract fun observeCollections(uid: String): Flow<List<FlashcardCollectionEntity>>
    @Query("SELECT * FROM flashcard_collections WHERE user_id = :uid AND id = :id")
    abstract suspend fun getCollection(uid: String, id: Long): FlashcardCollectionEntity?
    @Query("UPDATE flashcard_collections SET title = :title, description = :description WHERE user_id = :uid AND id = :id")
    abstract suspend fun updateCollection(uid: String, id: Long, title: String, description: String?): Int
    @Query("DELETE FROM flashcard_collections WHERE user_id = :uid AND id = :id")
    abstract suspend fun deleteCollection(uid: String, id: Long): Int

    @Query("SELECT * FROM flashcards WHERE collection_id = :collectionId ORDER BY id")
    abstract suspend fun cardsOf(collectionId: Long): List<FlashcardEntity>
    @Insert protected abstract suspend fun insertCard(card: FlashcardEntity): Long
    @Transaction
    open suspend fun addCard(uid: String, card: FlashcardEntity): Long {
        requireNotNull(getCollection(uid, card.collectionId)) { "Collection does not belong to this user" }
        return insertCard(card.copy(id = 0))
    }
    @Query("SELECT f.* FROM flashcards f JOIN flashcard_collections c ON f.collection_id = c.id WHERE c.user_id = :uid AND c.id = :collectionId ORDER BY f.id")
    abstract fun observeCards(uid: String, collectionId: Long): Flow<List<FlashcardEntity>>
    @Query("UPDATE flashcards SET word = :word, meaning = :meaning, example = :example, phonetic = :phonetic WHERE id = :id AND collection_id IN (SELECT id FROM flashcard_collections WHERE user_id = :uid)")
    abstract suspend fun updateCard(uid: String, id: Long, word: String, meaning: String, example: String?, phonetic: String?): Int
    @Query("DELETE FROM flashcards WHERE id = :id AND collection_id IN (SELECT id FROM flashcard_collections WHERE user_id = :uid)")
    abstract suspend fun deleteCard(uid: String, id: Long): Int
}
