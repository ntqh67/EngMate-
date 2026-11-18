package com.engmate.app.feature.vocabulary.domain

import com.engmate.app.feature.auth.domain.UserSession
import kotlinx.coroutines.flow.Flow

data class Topic(val id: Long, val title: String, val description: String?, val category: String?, val wordCount: Int)

data class Word(
    val id: Long,
    val topicId: Long?,
    val word: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val meaningEn: String,
    val meaningVi: String?,
    val exampleSentence: String?,
    val exampleMeaning: String?,
    val audioUrl: String?,
    val collocations: List<String>,
    val idioms: List<String>,
    val isCached: Boolean,
)

/** What a dictionary lookup gives us; mapped from the API response by the dictionary layer. */
data class CachedWord(
    val word: String,
    val meaningEn: String,
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val exampleSentence: String? = null,
    val audioUrl: String? = null,
)

/** Normalizes user input before a lookup: trimmed, lower-case, single spaces ("Look  Up" → "look up"). */
object WordKey {
    private val spaces = Regex("\\s+")
    fun of(raw: String): String = raw.trim().lowercase().replace(spaces, " ")
}

interface WordRepository {
    fun observeTopics(): Flow<List<Topic>>
    fun observeTopicWords(topicId: Long): Flow<List<Word>>
    fun observe(word: String): Flow<Word?>
    /** Local words starting with [prefix], shortest first. */
    suspend fun suggest(prefix: String, limit: Int = 6): List<String>
    /** Saves a dictionary lookup. Seed fields (Vietnamese, collocations, idioms, topic) of an existing word are kept. Throws [IllegalArgumentException]. */
    suspend fun cache(entry: CachedWord)
}

class SessionRequired : IllegalStateException("Bạn cần đăng nhập để dùng chức năng này")
class WordMissing : IllegalStateException("Từ này chưa có trong máy")

/** User data is only read or written for the signed-in user. */
fun UserSession.requireOwner(uid: String) {
    if (uid.isBlank() || user.value?.uid != uid) throw SessionRequired()
}
