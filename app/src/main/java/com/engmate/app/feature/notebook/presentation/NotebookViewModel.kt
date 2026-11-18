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
    fun close() { openedId.value = null }

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
                _dialog.value = null
                _message.value = "Đã tạo sổ tay “${current.title.trim()}”"
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
}
