package com.engmate.app.feature.grammar.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.grammar.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GrammarAdminUiState(
    val lessons: List<GrammarLesson> = emptyList(),
    val loading: Boolean = true,
    val editing: Boolean = false,
    val editingId: Long? = null,
    val input: LessonInput = LessonInput(),
    val errors: Map<LessonField, String> = emptyMap(),
    val busy: Boolean = false,
    val confirmDelete: GrammarLesson? = null,
    val message: String? = null,
)

@HiltViewModel
class GrammarAdminViewModel @Inject constructor(private val repository: GrammarRepository) : ViewModel() {
    private val mutable = MutableStateFlow(GrammarAdminUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLessons()
                .catch { mutable.update { it.copy(loading = false, message = "Không đọc được danh sách bài ngữ pháp.") } }
                .collect { lessons -> mutable.update { it.copy(lessons = lessons, loading = false) } }
        }
    }

    fun add() { if (!mutable.value.busy) mutable.update { it.copy(editing = true, editingId = null, input = LessonInput(), errors = emptyMap()) } }
    fun edit(lesson: GrammarLesson) {
        if (mutable.value.busy) return
        mutable.update { it.copy(editing = true, editingId = lesson.id, errors = emptyMap(),
            input = LessonInput(lesson.title, lesson.level, lesson.summary.orEmpty(), lesson.content, lesson.examples)) }
    }
    fun change(input: LessonInput) { if (!mutable.value.busy) mutable.update { it.copy(input = input, errors = emptyMap()) } }
    fun cancelEdit() { if (!mutable.value.busy) mutable.update { it.copy(editing = false, errors = emptyMap()) } }
    fun askDelete(lesson: GrammarLesson) { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = lesson) } }
    fun dismissDelete() { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = null) } }
    fun messageShown() = mutable.update { it.copy(message = null) }

    fun save() {
        val value = mutable.value
        if (value.busy || !value.editing) return
        val errors = LessonValidator.errors(value.input)
        if (errors.isNotEmpty()) { mutable.update { it.copy(errors = errors) }; return }
        val id = value.editingId
        mutate(if (id == null) "Đã thêm bài ngữ pháp." else "Đã lưu bài ngữ pháp.") {
            if (id == null) repository.create(value.input) else repository.update(id, value.input)
        }
    }

    fun delete() {
        val lesson = mutable.value.confirmDelete ?: return
        if (mutable.value.busy) return
        mutate("Đã xoá bài \"${lesson.title}\" và các câu hỏi của bài.") { repository.delete(lesson.id) }
    }

    private fun mutate(success: String, action: suspend () -> Unit) {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                action()
                mutable.update { it.copy(busy = false, editing = false, confirmDelete = null, errors = emptyMap(), message = success) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { it.copy(busy = false, confirmDelete = null, errors = (error as? InvalidLesson)?.errors.orEmpty(),
                    message = when (error) {
                        is InvalidLesson -> "Vui lòng kiểm tra thông tin nhập."
                        is LessonInUse -> "Không thể xoá: bài đã có câu hỏi được làm trong lịch sử."
                        is LessonMissing -> "Bài ngữ pháp không còn tồn tại."
                        else -> "Không thể lưu thay đổi. Hãy thử lại."
                    }) }
            }
        }
    }
}
