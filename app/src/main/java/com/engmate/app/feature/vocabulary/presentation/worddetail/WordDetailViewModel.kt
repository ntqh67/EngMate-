package com.engmate.app.feature.vocabulary.presentation.worddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.dictionary.domain.DictionaryRepository
import com.engmate.app.feature.dictionary.domain.LookupResult
import com.engmate.app.feature.notebook.domain.*
import com.engmate.app.feature.vocabulary.domain.Word
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WordDetailState(
    val loading: Boolean = true,
    val word: Word? = null,
    val note: WordNote? = null,
    val handbooks: List<Handbook> = emptyList(),
    /** Ids of the handbooks that contain this word. */
    val savedIn: Set<Long> = emptySet(),
) {
    val customMeaning get() = note?.customMeaning
    val savedTitles get() = handbooks.filter { it.id in savedIn }.map { it.title }
}

/** Each ✎ edits one card only; HANDBOOKS is the "Thêm vào Vocab Handbook" picker. */
enum class EditSheet { MEANING, EXAMPLE, COLLOCATION, NOTE, HANDBOOKS }

data class SheetState(val kind: EditSheet, val error: String? = null, val busy: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WordDetailViewModel @Inject constructor(
    private val session: UserSession,
    words: WordRepository,
    private val notes: WordNoteRepository,
    private val handbooks: HandbookRepository,
    private val dictionary: DictionaryRepository,
) : ViewModel() {
    private val key = MutableStateFlow<String?>(null)

    /** Opening a word is when it gets saved on the device (search results are not saved). */
    fun open(word: String) {
        val k = WordKey.of(word)
        if (key.value == k) return
        key.value = k
        viewModelScope.launch {
            when (dictionary.lookup(k, save = true)) {
                LookupResult.Offline -> _message.value = "Không có kết nối Internet. Không mở được từ này."
                LookupResult.Failed -> _message.value = "Không tra được từ điển trực tuyến. Hãy thử lại."
                else -> Unit
            }
        }
    }

    val state: StateFlow<WordDetailState> = combine(session.user, key) { user, k -> user to k }.flatMapLatest { (user, k) ->
        when {
            k == null -> flowOf(WordDetailState())
            user == null -> words.observe(k).map { WordDetailState(loading = false, word = it) }
            else -> combine(words.observe(k), notes.observe(user.uid, k), handbooks.observeHandbooks(user.uid),
                handbooks.observeHandbooksContaining(user.uid, k)) { w, n, h, saved -> WordDetailState(false, w, n, h, saved) }
        }
    }.catch { emit(WordDetailState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailState())

    private val _sheet = MutableStateFlow<SheetState?>(null)
    val sheet: StateFlow<SheetState?> = _sheet.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun showSheet(kind: EditSheet) { _sheet.value = SheetState(kind) }
    fun dismissSheet() { if (_sheet.value?.busy != true) _sheet.value = null }
    fun clearError() { _sheet.update { it?.copy(error = null) } }
    fun messageShown() { _message.value = null }

    /** Runs a write for the signed-in user; validation errors stay in the open sheet. */
    private fun write(done: String?, closeSheet: Boolean = true, block: suspend (uid: String, word: String) -> Unit) {
        val uid = session.user.value?.uid ?: run { _message.value = "Hãy đăng nhập để lưu thay đổi."; return }
        val word = key.value ?: return
        _sheet.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block(uid, word)
                if (closeSheet) _sheet.value = null else _sheet.update { it?.copy(busy = false) }
                done?.let { _message.value = it }
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) { // InvalidWordNote / InvalidHandbook carry the user-facing text
                if (_sheet.value != null) _sheet.update { it?.copy(busy = false, error = e.message) } else _message.value = e.message
            } catch (_: Exception) {
                val text = "Không lưu được. Hãy thử lại."
                if (_sheet.value != null) _sheet.update { it?.copy(busy = false, error = text) } else _message.value = text
            }
        }
    }

    fun saveMeaning(text: String) = write("Đã lưu nghĩa của bạn") { u, w -> notes.setMeaning(u, w, text) }
    fun addExample(en: String, vi: String) = write("Đã thêm ví dụ") { u, w -> notes.addExample(u, w, en, vi) }
    fun removeExample(index: Int) = write(null) { u, w -> notes.removeExample(u, w, index) }
    fun addCollocation(text: String) = write("Đã thêm cụm từ") { u, w -> notes.addCollocation(u, w, text) }
    fun removeCollocation(index: Int) = write(null) { u, w -> notes.removeCollocation(u, w, index) }
    fun saveNote(text: String) = write("Đã lưu ghi chú") { u, w -> notes.setNote(u, w, text) }

    fun toggleHandbook(id: Long) = write(null, closeSheet = false) { u, w ->
        if (id in state.value.savedIn) handbooks.removeWord(u, id, w) else handbooks.addWord(u, id, w)
    }

    fun createHandbookAndAdd(title: String) = write("Đã tạo “${title.trim()}” và thêm từ", closeSheet = false) { u, w ->
        handbooks.addWord(u, handbooks.create(u, title), w)
    }
}
