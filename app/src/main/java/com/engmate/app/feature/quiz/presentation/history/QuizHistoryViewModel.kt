package com.engmate.app.feature.quiz.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.quiz.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AnswerFilter(val label: String) { ALL("Tất cả"), WRONG("Câu sai"), CORRECT("Câu đúng") }

data class QuizHistoryUiState(
    val user: SessionUser? = null,
    val attempts: List<AttemptSummary> = emptyList(),
    val loading: Boolean = true,
    val selectedId: Long? = null,
    val detail: AttemptDetail? = null,
    val filter: AnswerFilter = AnswerFilter.ALL,
    val busy: Boolean = false,
    val confirmDelete: AttemptSummary? = null,
    val message: String? = null,
) {
    val visibleAnswers: List<AnsweredQuestion> get() = detail?.answers.orEmpty().filter {
        when (filter) { AnswerFilter.ALL -> true; AnswerFilter.WRONG -> !it.isCorrect; AnswerFilter.CORRECT -> it.isCorrect }
    }
}

@HiltViewModel
class QuizHistoryViewModel @Inject constructor(
    private val attempts: QuizAttemptRepository,
    private val questions: QuestionRepository,
    private val grammar: GrammarRepository,
    private val session: UserSession,
) : ViewModel() {
    private val mutable = MutableStateFlow(QuizHistoryUiState())
    val state = mutable.asStateFlow()
    private var history: Job? = null
    private var detail: Job? = null

    init {
        viewModelScope.launch {
            session.user.collect { user ->
                history?.cancel(); detail?.cancel()
                mutable.value = QuizHistoryUiState(user = user, loading = user != null)
                if (user != null) observe(user.uid)
            }
        }
    }

    private fun observe(uid: String) {
        history = viewModelScope.launch {
            try {
                attempts.observeHistory(uid).collect { list -> mutable.update { it.copy(attempts = list, loading = false) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loading = false, message = "Không đọc được lịch sử làm bài.") } }
        }
    }

    fun open(id: Long) {
        val uid = mutable.value.user?.uid ?: return
        detail?.cancel()
        mutable.update { it.copy(selectedId = id, detail = null, filter = AnswerFilter.ALL) }
        detail = viewModelScope.launch {
            try {
                attempts.observeAttempt(uid, id).collect { d ->
                    if (d == null) mutable.update { it.copy(selectedId = null, detail = null) }
                    else mutable.update { it.copy(detail = d) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(selectedId = null, message = "Không đọc được chi tiết lượt làm.") } }
        }
    }

    fun close() { detail?.cancel(); mutable.update { it.copy(selectedId = null, detail = null) } }
    fun filter(filter: AnswerFilter) = mutable.update { it.copy(filter = filter) }
    fun askDelete(attempt: AttemptSummary) { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = attempt) } }
    fun dismissDelete() { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = null) } }
    fun messageShown() = mutable.update { it.copy(message = null) }

    fun delete() {
        val target = mutable.value.confirmDelete ?: return
        val uid = mutable.value.user?.uid ?: return
        if (mutable.value.busy) return
        perform {
            attempts.delete(uid, target.id)
            if (mutable.value.selectedId == target.id) close()
            "Đã xoá lượt làm khỏi lịch sử."
        }
    }

    /** Debug only: stores an attempt built from seed questions, alternating lessons and a placement test. */
    fun createSample() {
        val uid = mutable.value.user?.uid ?: return
        if (mutable.value.busy) return
        perform {
            val count = mutable.value.attempts.size
            val lessons = grammar.observeLessons().first()
            val placement = count % 3 == 2 || lessons.isEmpty()
            val lesson = lessons.getOrNull(count % lessons.size.coerceAtLeast(1)).takeUnless { placement }
            val pool = if (lesson == null) questions.placementQuestions() else questions.questionsForLesson(lesson.id)
            if (pool.isEmpty() || placement && pool.size < QuizScoring.PLACEMENT_QUESTION_COUNT)
                throw InvalidAttempt("Chưa có đủ câu hỏi mẫu để tạo lượt làm.")
            val random = Random(System.nanoTime())
            val answers = pool.map { q -> sampleAnswer(q, random.nextFloat() < 0.7f, random) }
            val r = attempts.saveAttempt(uid, AttemptDraft(lessonId = lesson?.id, isPlacement = lesson == null, answers = answers))
            buildString {
                append("Đã tạo lượt làm mẫu: ${r.correctCount}/${r.totalQuestions} câu đúng · +${r.xpEarned} XP")
                r.level?.let { append(if (r.levelSaved) ". Trình độ: $it (đã cập nhật hồ sơ)" else ". Trình độ: $it (chưa cập nhật được hồ sơ)") }
            }
        }
    }

    private fun sampleAnswer(q: Question, correct: Boolean, random: Random): AnswerDraft = when (q.answerType) {
        AnswerType.MULTIPLE_CHOICE -> AnswerDraft(q.id, selectedOptionId =
            (if (correct) q.correctOptions else q.options.filterNot { it.isCorrect }).randomOrNull(random)?.id)
        // Extra spaces and capitals show that FILL_BLANK grading ignores them.
        AnswerType.FILL_BLANK -> AnswerDraft(q.id, answerText =
            if (correct) "  " + q.correctOptions.first().content.uppercase() + " " else "sai")
    }

    private fun perform(action: suspend () -> String) {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val success = action()
                mutable.update { it.copy(busy = false, confirmDelete = null, message = success) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { it.copy(busy = false, confirmDelete = null, message = when (error) {
                    is ProfileRequired -> "Hãy tạo hồ sơ cá nhân trước khi lưu kết quả (mục Hồ sơ cá nhân)."
                    is AttemptMissing -> "Lượt làm không còn tồn tại."
                    is AttemptSessionRequired -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
                    is InvalidAttempt -> error.message ?: "Lượt làm không hợp lệ."
                    else -> "Không thể thực hiện. Hãy thử lại."
                }) }
            }
        }
    }
}
