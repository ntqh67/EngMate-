package com.engmate.app.feature.vocabulary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class TopicWithWordCount(
    @Embedded val topic: TopicEntity,
    @ColumnInfo(name = "total_words") val totalWords: Int,
)

@Dao
interface VocabularyDao {
    @Insert suspend fun insertTopic(topic: TopicEntity): Long
    @Update suspend fun updateTopic(topic: TopicEntity): Int
    @Query("DELETE FROM topics WHERE id = :id") suspend fun deleteTopic(id: Long): Int
    @Query("SELECT t.*, (SELECT COUNT(*) FROM words w WHERE w.topic_id = t.id) AS total_words FROM topics t ORDER BY t.title, t.id")
    fun observeTopics(): Flow<List<TopicWithWordCount>>
    @Query("SELECT * FROM topics WHERE id = :id") suspend fun getTopic(id: Long): TopicEntity?

    @Insert suspend fun insertWord(word: WordEntity): Long
    @Update suspend fun updateWord(word: WordEntity): Int
    @Query("DELETE FROM words WHERE id = :id") suspend fun deleteWord(id: Long): Int
    @Query("SELECT * FROM words WHERE id = :id") suspend fun getWord(id: Long): WordEntity?
    @Query("SELECT * FROM words WHERE word = :word COLLATE NOCASE") suspend fun findWord(word: String): WordEntity?
    @Query("SELECT * FROM words WHERE topic_id = :topicId ORDER BY word, id")
    fun observeWords(topicId: Long): Flow<List<WordEntity>>
    // instr treats %, _ and quotes as literal input instead of wildcard SQL.
    @Query("SELECT * FROM words WHERE instr(lower(word), lower(trim(:query))) > 0 OR instr(coalesce(meaning_vi, ''), trim(:query)) > 0 ORDER BY word, id LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<WordEntity>
}
