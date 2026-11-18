package com.engmate.app.feature.notebook.domain

import com.engmate.app.core.database.WordLearningStatus
import kotlinx.coroutines.flow.Flow

/** A user-created Vocab Handbook (table flashcard_collections). */
data class Handbook(val id: Long, val title: String, val description: String?, val wordCount: Int, val knownCount: Int, val createdAt: Long?)

/** One card of a handbook (table flashcards) with the word's live data. */
data class HandbookEntry(
    val cardId: Long,
    val word: String,
    /** Meaning copied onto the card when it was added. */
    val cardMeaning: String,
    val meaningVi: String?,
    val customMeaning: String?,
    val status: WordLearningStatus,
) {
    /** The user's own meaning wins, then the seed Vietnamese meaning, then the meaning saved on the card. */
    val displayMeaning get() = customMeaning ?: meaningVi ?: cardMeaning
}

data class WordExample(val en: String, val vi: String)

/** The user's own additions to a word (table word_notes). Dictionary content is never changed. */
data class WordNote(
    val word: String,
    val customMeaning: String?,
    val examples: List<WordExample>,
    val collocations: List<String>,
    val idioms: List<String>,
    val personalNote: String?,
    val updatedAt: Long?,
)

interface HandbookRepository {
    fun observeHandbooks(uid: String): Flow<List<Handbook>>
    fun observeEntries(uid: String, handbookId: Long): Flow<List<HandbookEntry>>
    /** Ids of the user's handbooks that contain [word]; drives the "Đã lưu trong N sổ tay" button. */
    fun observeHandbooksContaining(uid: String, word: String): Flow<Set<Long>>
    /** Returns the new id. Throws [InvalidHandbook]. */
    suspend fun create(uid: String, title: String, description: String = ""): Long
    /** Throws [InvalidHandbook] or [HandbookMissing]. */
    suspend fun rename(uid: String, handbookId: Long, title: String)
    /** Removes the handbook and its cards; words, notes and progress stay. Throws [HandbookMissing]. */
    suspend fun delete(uid: String, handbookId: Long)
    /** Does nothing if the word is already there. Throws [HandbookMissing] or [com.engmate.app.feature.vocabulary.domain.WordMissing]. */
    suspend fun addWord(uid: String, handbookId: Long, word: String)
    /** Throws [HandbookMissing]. */
    suspend fun removeWord(uid: String, handbookId: Long, word: String)
}

interface WordNoteRepository {
    fun observe(uid: String, word: String): Flow<WordNote?>
    /** A blank meaning brings back the dictionary meaning. Throws [InvalidWordNote]. */
    suspend fun setMeaning(uid: String, word: String, meaning: String)
    /** Throws [InvalidWordNote]. */
    suspend fun addExample(uid: String, word: String, en: String, vi: String = "")
    suspend fun removeExample(uid: String, word: String, index: Int)
    /** Throws [InvalidWordNote], also when the word already has this collocation. */
    suspend fun addCollocation(uid: String, word: String, text: String)
    suspend fun removeCollocation(uid: String, word: String, index: Int)
    /** Throws [InvalidWordNote]. */
    suspend fun setNote(uid: String, word: String, note: String)
}

object NotebookRules {
    const val MAX_TITLE = 40
    const val MAX_MEANING = 120
    const val MAX_EXAMPLE = 200
    const val MAX_COLLOCATION = 50
    const val MAX_NOTE = 300

    /** Null when [title] is valid; [taken] are the user's other handbook titles. */
    fun titleError(title: String, taken: List<String>): String? {
        val value = title.trim()
        return when {
            value.isEmpty() -> "Nhập tên sổ tay"
            value.length > MAX_TITLE -> "Tên tối đa $MAX_TITLE ký tự"
            taken.any { it.equals(value, ignoreCase = true) } -> "Tên sổ tay đã tồn tại"
            else -> null
        }
    }
}

class InvalidHandbook(message: String) : IllegalArgumentException(message)
class HandbookMissing : IllegalStateException("Sổ tay không còn tồn tại")
class InvalidWordNote(message: String) : IllegalArgumentException(message)
