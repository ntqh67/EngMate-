package com.engmate.app.feature.quiz.presentation.questionbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.quiz.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QuestionEditor(
    val id: Long? = null,
    val category: QuestionCategory = QuestionCategory.GRAMMAR,
    /** TODO(Khang): replace the numeric topic id with a topic picker once the topics table exists. */
    val topicText: String = "",
    val lessonId: Long? = null,
    val answerType: AnswerType = AnswerType.MULTIPLE_CHOICE,
    val content: String = "",
    val explanation: String = "",
    val choices: List<String> = List(QuestionValidator.MULTIPLE_CHOICE_OPTIONS) { "" },
    val correctIndex: Int? = null,
    val accepted: List<String> = listOf(""),
    val errors: Map<QuestionField, String> = emptyMap(),
) {
    fun toInput() = QuestionInput(
        topicId = topicText.trim().toLongOrNull().takeIf { category == QuestionCategory.VOCABULARY },
        lessonId = lessonId.takeIf { category == QuestionCategory.GRAMMAR },
        isPlacement = category == QuestionCategory.PLACEMENT,
        answerType = answerType, content = content, explanation = explanation,
        options = when (answerType) {
            AnswerType.MULTIPLE_CHOICE -> choices.mapIndexed { i, c -> OptionInput(c, i == correctIndex) }
            AnswerType.FILL_BLANK -> accepted.map { OptionInput(it, true) }
        },
    )

    fun validate(): Map<QuestionField, String> {
        val scope = when {
            category == QuestionCategory.VOCABULARY && topicText.trim().toLongOrNull() == null -> "Nhập mã chủ đề từ vựng (số)."
            category == QuestionCategory.GRAMMAR && lessonId == null -> "Chọn bài ngữ pháp cho câu hỏi."
            else -> null
        }
        val errors = QuestionValidator.errors(toInput())
        return if (scope != null) errors + (QuestionField.CATEGORY to scope) else errors
    }

    companion object {
        fun from(q: Question) = QuestionEditor(
            id = q.id, category = q.category ?: QuestionCategory.GRAMMAR, topicText = q.topicId?.toString().orEmpty(),
            lessonId = q.lessonId, answerType = q.answerType, content = q.content, explanation = q.explanation.orEmpty(),
            choices = if (q.answerType == AnswerType.MULTIPLE_CHOICE) q.options.map { it.content }
                .let { it + List((QuestionValidator.MULTIPLE_CHOICE_OPTIONS - it.size).coerceAtLeast(0)) { "" } }
                else List(QuestionValidator.MULTIPLE_CHOICE_OPTIONS) { "" },
            correctIndex = if (q.answerType == AnswerType.MULTIPLE_CHOICE) q.options.indexOfFirst { it.isCorrect }.takeIf { it >= 0 } else null,
            accepted = if (q.answerType == AnswerType.FILL_BLANK) q.options.map { it.content }.ifEmpty { listOf("") } else listOf(""),
        )
    }
}

data class QuestionBankUiState(
    val questions: List<Question> = emptyList(),
    val lessons: List<GrammarLesson> = emptyList(),
    val loading: Boolean = true,
    val category: QuestionCategory? = null,
    val lessonFilter: Long? = null,
    val topicFilter: Long? = null,
    val query: String = "",
    val editor: QuestionEditor? = null,
    val busy: Boolean = false,
    val confirmDelete: Question? = null,
    val message: String? = null,
) {
    val topics: List<Long> get() = questions.mapNotNull { it.topicId }.distinct().sorted()
    val visible: List<Question> get() = questions.filter { q ->
        (category == null || q.category == category) &&
            (lessonFilter == null || q.lessonId == lessonFilter) &&
            (topicFilter == null || q.topicId == topicFilter) &&
            (query.isBlank() || q.content.contains(query.trim(), ignoreCase = true) ||
                q.options.any { it.content.contains(query.trim(), ignoreCase = true) })
    }
    fun lessonTitle(id: Long?) = lessons.firstOrNull { it.id == id }?.title
}

@HiltViewModel
class QuestionBankViewModel @Inject constructor(
    private val repository: QuestionRepository,
    grammar: GrammarRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(QuestionBankUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeQuestions(), grammar.observeLessons(), ::Pair)
                .catch { mutable.update { it.copy(loading = false, message = "Không đọc được ngân hàng câu hỏi.") } }
                .collect { (questions, lessons) -> mutable.update { it.copy(questions = questions, lessons = lessons, loading = false) } }
        }
    }

    fun filterCategory(category: QuestionCategory?) = mutable.update { it.copy(category = category, lessonFilter = null, topicFilter = null) }
    fun filterLesson(id: Long?) = mutable.update { it.copy(lessonFilter = id) }
    fun filterTopic(id: Long?) = mutable.update { it.copy(topicFilter = id) }
    fun search(query: String) = mutable.update { it.copy(query = query) }
    fun messageShown() = mutable.update { it.copy(message = null) }

    fun add() {
        val s = mutable.value
        if (s.busy) return
        val category = s.category ?: QuestionCategory.GRAMMAR
        mutable.update { it.copy(editor = QuestionEditor(category = category,
            lessonId = s.lessonFilter ?: s.lessons.firstOrNull()?.id, topicText = s.topicFilter?.toString().orEmpty())) }
    }
    fun edit(question: Question) { if (!mutable.value.busy) mutable.update { it.copy(editor = QuestionEditor.from(question)) } }
    fun change(editor: QuestionEditor) { if (!mutable.value.busy) mutable.update { it.copy(editor = editor.copy(errors = emptyMap())) } }
    fun cancelEdit() { if (!mutable.value.busy) mutable.update { it.copy(editor = null) } }
    fun askDelete(question: Question) { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = question) } }
    fun dismissDelete() { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = null) } }

    fun save() {
        val editor = mutable.value.editor ?: return
        if (mutable.value.busy) return
        val errors = editor.validate()
        if (errors.isNotEmpty()) { mutable.update { it.copy(editor = editor.copy(errors = errors)) }; return }
        val id = editor.id
        mutate(if (id == null) "Đã thêm câu hỏi." else "Đã lưu câu hỏi.") {
            if (id == null) repository.create(editor.toInput()) else repository.update(id, editor.toInput())
        }
    }

    fun delete() {
        val question = mutable.value.confirmDelete ?: return
        if (mutable.value.busy) return
        mutate("Đã xoá câu hỏi.") { repository.delete(question.id) }
    }

    private fun mutate(success: String, action: suspend () -> Unit) {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                action()
                mutable.update { it.copy(busy = false, editor = null, confirmDelete = null, message = success) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { s -> s.copy(busy = false, confirmDelete = null,
                    editor = (error as? InvalidQuestion)?.let { e -> s.editor?.copy(errors = e.errors) } ?: s.editor,
                    message = when (error) {
                        is InvalidQuestion -> "Vui lòng kiểm tra thông tin nhập."
                        is QuestionInUse -> "Câu hỏi đã có trong lịch sử làm bài, không thể sửa hoặc xoá."
                        is QuestionMissing -> "Câu hỏi không còn tồn tại."
                        else -> "Không thể lưu thay đổi. Hãy thử lại."
                    }) }
            }
        }
    }
}
