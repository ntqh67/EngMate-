package com.engmate.app.feature.notebook.data

import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.notebook.data.local.NotebookDao
import com.engmate.app.feature.notebook.data.local.WordNoteEntity
import com.engmate.app.feature.notebook.domain.*
import com.engmate.app.feature.notebook.domain.NotebookRules.MAX_COLLOCATION
import com.engmate.app.feature.notebook.domain.NotebookRules.MAX_EXAMPLE
import com.engmate.app.feature.notebook.domain.NotebookRules.MAX_MEANING
import com.engmate.app.feature.notebook.domain.NotebookRules.MAX_NOTE
import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordMissing
import com.engmate.app.feature.vocabulary.domain.requireOwner
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * custom_examples is a List<String> on main. shortcut: each example is stored as a JSON object string
 * {"en","vi"} so the Vietnamese translation survives; plain strings still read as English-only.
 * Upgrade when the team agrees on a structured column (v4 proposal).
 */
internal object ExampleCodec {
    fun encode(example: WordExample): String = JSONObject().put("en", example.en).put("vi", example.vi).toString()
    fun decode(value: String): WordExample =
        if (value.trimStart().startsWith("{")) runCatching { JSONObject(value).let { WordExample(it.optString("en"), it.optString("vi")) } }
            .getOrElse { WordExample(value, "") }
        else WordExample(value, "")
}

class RoomWordNoteRepository @Inject constructor(
    private val dao: NotebookDao, private val words: VocabularyDao, private val session: UserSession, private val clock: Clock,
) : WordNoteRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(uid: String, word: String): Flow<WordNote?> {
        session.requireOwner(uid)
        return words.observeWord(WordKey.of(word)).flatMapLatest { entry ->
            if (entry == null) flowOf(null) else dao.observeWordNote(uid, entry.id).map { it?.toDomain(entry.word) }
        }
    }

    private fun WordNoteEntity.toDomain(word: String) = WordNote(word, customMeaning, customExamples.map(ExampleCodec::decode),
        customCollocations, customIdioms, personalNote, updatedAt)

    /** Reads the user's note for [word] (or a blank one), applies [change] and saves it. */
    private suspend fun edit(uid: String, word: String, change: (WordEntity, WordNoteEntity) -> WordNoteEntity) {
        session.requireOwner(uid)
        val entry = words.findWord(WordKey.of(word)) ?: throw WordMissing()
        val current = dao.getWordNote(uid, entry.id) ?: WordNoteEntity(userUid = uid, wordId = entry.id)
        dao.saveWordNote(change(entry, current).copy(updatedAt = clock.millis()))
    }

    private fun check(ok: Boolean, message: String) { if (!ok) throw InvalidWordNote(message) }

    override suspend fun setMeaning(uid: String, word: String, meaning: String) {
        val value = meaning.trim()
        check(value.length <= MAX_MEANING, "Nghĩa tối đa $MAX_MEANING ký tự")
        edit(uid, word) { _, note -> note.copy(customMeaning = value.ifEmpty { null }) }
    }

    override suspend fun addExample(uid: String, word: String, en: String, vi: String) {
        val example = WordExample(en.trim(), vi.trim())
        check(example.en.isNotEmpty(), "Nhập câu ví dụ tiếng Anh")
        check(example.en.length <= MAX_EXAMPLE && example.vi.length <= MAX_EXAMPLE, "Ví dụ tối đa $MAX_EXAMPLE ký tự")
        edit(uid, word) { _, note -> note.copy(customExamples = note.customExamples + ExampleCodec.encode(example)) }
    }

    override suspend fun removeExample(uid: String, word: String, index: Int) =
        edit(uid, word) { _, note -> note.copy(customExamples = note.customExamples.filterIndexed { i, _ -> i != index }) }

    override suspend fun addCollocation(uid: String, word: String, text: String) {
        val value = text.trim().replace(Regex("\\s+"), " ")
        check(value.isNotEmpty(), "Nhập cụm từ")
        check(value.length <= MAX_COLLOCATION, "Cụm từ tối đa $MAX_COLLOCATION ký tự")
        edit(uid, word) { entry, note ->
            check((entry.collocations + note.customCollocations).none { it.equals(value, ignoreCase = true) }, "Cụm từ này đã có")
            note.copy(customCollocations = note.customCollocations + value)
        }
    }

    override suspend fun removeCollocation(uid: String, word: String, index: Int) =
        edit(uid, word) { _, note -> note.copy(customCollocations = note.customCollocations.filterIndexed { i, _ -> i != index }) }

    override suspend fun setNote(uid: String, word: String, note: String) {
        val value = note.trim()
        check(value.length <= MAX_NOTE, "Ghi chú tối đa $MAX_NOTE ký tự")
        edit(uid, word) { _, current -> current.copy(personalNote = value.ifEmpty { null }) }
    }
}
