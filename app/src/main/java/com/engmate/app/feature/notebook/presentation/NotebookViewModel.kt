package com.engmate.app.feature.notebook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.notebook.domain.Handbook
import com.engmate.app.feature.notebook.domain.HandbookEntry
import com.engmate.app.feature.notebook.domain.HandbookRepository
import com.engmate.app.feature.notebook.domain.InvalidHandbook
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NotebookUiState(
    val handbooks: List<Handbook> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** Open handbook and its words; null shows the list. */
    val opened: Handbook? = null,
    val entries: List<HandbookEntry> = emptyList(),
)

/** Entries whose word or meaning contains [query], ignoring case; all of them for a blank query. */
internal fun filterEntries(entries: List<HandbookEntry>, query: String): List<HandbookEntry> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { it.word.contains(q, ignoreCase = true) || it.displayMeaning.contains(q, ignoreCase = true) }
}

/** A word just removed from a handbook, kept so the snackbar can undo it. */
data class RemovedWord(val handbookId: Long, val word: String)

/** Create-handbook dialog: visible while non-null. */
data class CreateDialog(val title: String = "", val error: String? = null, val busy: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val session: UserSession,
    private val handbooks: HandbookRepository,
) : ViewModel() {
    private val openedId = MutableStateFlow<Long?>(null)

    val state: StateFlow<NotebookUiState> = session.user.flatMapLatest { user ->
        if (user == null) flowOf(NotebookUiState(loading = false))
        else handbooks.observeHandbooks(user.uid).combine(openedId) { list, id -> list to list.firstOrNull { it.id == id } }
            .flatMapLatest { (list, opened) ->
                if (opened == null) flowOf(NotebookUiState(list, loading = false))
                else handbooks.observeEntries(user.uid, opened.id).map { NotebookUiState(list, loading = false, opened = opened, entries = it) }
            }
    }.catch { emit(NotebookUiState(loading = false, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    private val _dialog = MutableStateFlow<CreateDialog?>(null)
    val dialog: StateFlow<CreateDialog?> = _dialog.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun open(id: Long) { openedId.value = id }
    fun close() { openedId.value = null; _query.value = "" }

    fun showCreate() { _dialog.value = CreateDialog() }
    fun dismissCreate() { if (_dialog.value?.busy != true) _dialog.value = null }
    fun onTitleChange(value: String) { _dialog.update { it?.copy(title = value, error = null) } }

    fun create() {
        val current = _dialog.value ?: return
        val uid = session.user.value?.uid ?: return
        _dialog.value = current.copy(busy = true)
        viewModelScope.launch {
            try {
                handbooks.create(uid, current.title)
                // Message first: callers treat the dialog closing as "done".
                _message.value = "Đã tạo sổ tay “${current.title.trim()}”"
                _dialog.value = null
            } catch (e: InvalidHandbook) {
                _dialog.value = current.copy(error = e.message, busy = false)
            } catch (e: Exception) {
                _dialog.value = current.copy(error = "Không tạo được sổ tay. Hãy thử lại.", busy = false)
            }
        }
    }

    fun deleteOpened() {
        val uid = session.user.value?.uid ?: return
        val handbook = state.value.opened ?: return
        viewModelScope.launch {
            runCatching { handbooks.delete(uid, handbook.id) }
                .onSuccess { close(); _message.value = "Đã xoá “${handbook.title}”" }
                .onFailure { _message.value = "Không xoá được sổ tay. Hãy thử lại." }
        }
    }

    fun messageShown() { _message.value = null }

    private val _query = MutableStateFlow("")
    /** Search text inside the open handbook. */
    val query: StateFlow<String> = _query.asStateFlow()
    fun search(text: String) { _query.value = text.take(50) }

    private val _removed = MutableStateFlow<RemovedWord?>(null)
    /** Set after a swipe-to-remove until the snackbar is gone; the screen offers Hoàn tác meanwhile. */
    val removed: StateFlow<RemovedWord?> = _removed.asStateFlow()

    /** Takes [word] out of the open handbook; the word, its notes and review progress stay on the device. */
    fun removeWord(word: String) {
        val uid = session.user.value?.uid ?: return
        val handbook = state.value.opened ?: return
        viewModelScope.launch {
            runCatching { handbooks.removeWord(uid, handbook.id, word) }
                .onSuccess { _removed.value = RemovedWord(handbook.id, word) }
                .onFailure { _message.value = "Không gỡ được “$word”. Hãy thử lại." }
        }
    }

    /** Puts the last removed word back into its handbook. */
    fun undoRemove() {
        val uid = session.user.value?.uid ?: return
        val last = _removed.value ?: return
        _removed.value = null
        viewModelScope.launch {
            runCatching { handbooks.addWord(uid, last.handbookId, last.word) }
                .onFailure { _message.value = "Không hoàn tác được. Hãy thêm lại “${last.word}” từ Chi tiết từ." }
        }
    }

    fun removedShown() { _removed.value = null }
}
