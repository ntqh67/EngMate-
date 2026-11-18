package com.engmate.app.feature.quiz.presentation.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.core.contracts.TopicLookup
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.quiz.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** The learner's answer to one question. [correct] is only meaningful once [checked]. */
data class QuizAnswer(val selectedOptionId: Long? = null, val text: String = "", val checked: Boolean = false, val correct: Boolean = false)

/** Why saving the attempt failed; each maps to a message and, for some, an action on screen. */
sealed interface QuizSaveError {
    data object SignInRequired : QuizSaveError
    data object ProfileRequired : QuizSaveError
    data class Invalid(val message: String) : QuizSaveError
    data object Failed : QuizSaveError
}

data class QuizUiState(
    val source: QuizSource? = null,
    val title: String = "",
    val loading: Boolean = true,
    /** Not enough questions to start; shown as an empty state with a back button. */
    val emptyMessage: String? = null,
    /** Loading failed; shown with a "Thử lại" button. */
    val loadError: String? = null,
    val questions: List<Question> = emptyList(),
    val answers: List<QuizAnswer> = emptyList(),
    val index: Int = 0,
    val saving: Boolean = false,
    val saveError: QuizSaveError? = null,
    val confirmExit: Boolean = false,
    /** Set once the attempt is stored; the screen then leaves. */
    val result: AttemptResult? = null,
) {
    val current: Question? get() = questions.getOrNull(index)
    val currentAnswer: QuizAnswer get() = answers.getOrNull(index) ?: QuizAnswer()
    val isLast: Boolean get() = index == questions.lastIndex
    val canCheck: Boolean get() {
        val q = current ?: return false
        val a = currentAnswer
        if (a.checked || saving) return false
        return when (q.answerType) {
            AnswerType.MULTIPLE_CHOICE -> a.selectedOptionId != null
            AnswerType.FILL_BLANK -> a.text.isNotBlank()
        }
    }
    /** Leaving now would throw away work: something was checked, or the current question has input. */
    val hasProgress: Boolean get() = answers.any { it.checked } ||
        currentAnswer.selectedOptionId != null || currentAnswer.text.isNotBlank()
}

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val questions: QuestionRepository,
    private val attempts: QuizAttemptRepository,
    private val grammar: GrammarRepository,
    private val topics: TopicLookup,
    private val session: UserSession,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(QuizUiState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null

    /**
     * Starts a quiz from [source]. Calling it again for the same unfinished quiz keeps the progress
     * (rotation, returning to the screen); a finished or abandoned quiz starts over.
     */
    fun start(source: QuizSource) {
        val s = mutable.value
        if (s.source == source && s.result == null && (s.loading || s.questions.isNotEmpty())) return
        val restore = saved.get<String>(KEY_SOURCE) == source.encode() && s.result == null
        if (!restore) clearSaved()
        load(source, restore)
    }

    fun retryLoad() { mutable.value.source?.let { load(it, restore = false) } }

    private fun load(source: QuizSource, restore: Boolean) {
        loadJob?.cancel()
        mutable.value = QuizUiState(source = source)
        saved[KEY_SOURCE] = source.encode()
        loadJob = viewModelScope.launch {
            try {
                val title = titleOf(source)
                val pool = when (source) {
                    is QuizSource.Lesson -> questions.questionsForLesson(source.lessonId)
                    is QuizSource.Topic -> questions.questionsForTopic(source.topicId)
                    QuizSource.Placement -> questions.placementQuestions()
                }
                val empty = when (source) {
                    is QuizSource.Lesson -> "Bài này chưa có câu hỏi".takeIf { pool.isEmpty() }
                    is QuizSource.Topic -> "Chủ đề cần ít nhất $QUIZ_SIZE câu hỏi để làm quiz".takeIf { pool.size < QUIZ_SIZE }
                    QuizSource.Placement -> ("Bài kiểm tra trình độ cần đủ ${QuizScoring.PLACEMENT_QUESTION_COUNT} câu hỏi, " +
                        "hiện mới có ${pool.size} câu.").takeIf { pool.size < QuizScoring.PLACEMENT_QUESTION_COUNT }
                }
                if (empty != null) {
                    mutable.update { it.copy(title = title, loading = false, emptyMessage = empty) }
                    return@launch
                }
                val restoredOrder = if (restore) restored(pool) else null
                val picked = restoredOrder ?: pick(source, pool)
                val answers = if (restoredOrder != null) restoredAnswers(picked) else picked.map { QuizAnswer() }
                val index = if (restoredOrder != null) (saved.get<Int>(KEY_INDEX) ?: 0).coerceIn(0, picked.lastIndex) else 0
                mutable.update { it.copy(title = title, loading = false, questions = picked, answers = answers, index = index) }
                persist()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.update { it.copy(loading = false, loadError = "Không tải được câu hỏi. Hãy thử lại.") }
            }
        }
    }

    private suspend fun titleOf(source: QuizSource): String = when (source) {
        is QuizSource.Lesson -> runCatching { grammar.observeLesson(source.lessonId).first()?.title }.getOrNull() ?: "Bài ngữ pháp"
        is QuizSource.Topic -> runCatching { topics.titleOf(source.topicId) }.getOrNull() ?: "Chủ đề #${source.topicId}"
        QuizSource.Placement -> PLACEMENT_TITLE
    }

    private fun pick(source: QuizSource, pool: List<Question>): List<Question> = when (source) {
        // Placement keeps its fixed order so every learner gets the same test.
        QuizSource.Placement -> pool.take(QuizScoring.PLACEMENT_QUESTION_COUNT)
        else -> pool.shuffled().take(QUIZ_SIZE)
    }

    fun select(optionId: Long) = edit { q, a ->
        if (q.answerType != AnswerType.MULTIPLE_CHOICE || q.options.none { it.id == optionId }) a else a.copy(selectedOptionId = optionId)
    }

    fun type(text: String) = edit { q, a -> if (q.answerType == AnswerType.FILL_BLANK) a.copy(text = text) else a }

    private inline fun edit(change: (Question, QuizAnswer) -> QuizAnswer) {
        val s = mutable.value
        val q = s.current ?: return
        if (s.currentAnswer.checked || s.saving) return
        mutable.value = s.copy(answers = s.answers.replace(s.index, change(q, s.currentAnswer)))
        persist()
    }

    fun check() {
        val s = mutable.value
        if (!s.canCheck) return
        val q = s.current ?: return
        val a = s.currentAnswer
        val correct = QuizScoring.grade(q, a.selectedOptionId, a.text)
        mutable.value = s.copy(answers = s.answers.replace(s.index, a.copy(checked = true, correct = correct)))
        persist()
    }

    /** Moves on after a checked question; on the last one it saves the attempt. */
    fun next() {
        val s = mutable.value
        if (!s.currentAnswer.checked || s.saving) return
        if (s.isLast) save() else { mutable.value = s.copy(index = s.index + 1); persist() }
    }

    fun retrySave() = save()

    private fun save() {
        val s = mutable.value
        val source = s.source ?: return
        if (s.saving || s.result != null || s.answers.any { !it.checked }) return
        mutable.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val error: QuizSaveError? = try {
                val uid = session.user.value?.uid ?: throw AttemptSessionRequired()
                val draft = AttemptDraft(
                    topicId = (source as? QuizSource.Topic)?.topicId,
                    lessonId = (source as? QuizSource.Lesson)?.lessonId,
                    isPlacement = source == QuizSource.Placement,
                    answers = s.questions.zip(s.answers) { q, a ->
                        when (q.answerType) {
                            AnswerType.MULTIPLE_CHOICE -> AnswerDraft(q.id, selectedOptionId = a.selectedOptionId)
                            AnswerType.FILL_BLANK -> AnswerDraft(q.id, answerText = a.text)
                        }
                    })
                val result = attempts.saveAttempt(uid, draft)
                clearSaved()
                mutable.update { it.copy(saving = false, result = result) }
                null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AttemptSessionRequired) { QuizSaveError.SignInRequired }
            catch (_: ProfileRequired) { QuizSaveError.ProfileRequired }
            catch (e: InvalidAttempt) { QuizSaveError.Invalid(e.message ?: "Lượt làm không hợp lệ.") }
            catch (_: Exception) { QuizSaveError.Failed }
            // Answers stay on screen so the learner can retry.
            if (error != null) mutable.update { it.copy(saving = false, saveError = error) }
        }
    }

    /** Returns true when the screen may close right away; otherwise asks for confirmation first. */
    fun requestExit(): Boolean {
        val s = mutable.value
        if (s.saving) return false
        if (!s.hasProgress || s.result != null) { abandon(); return true }
        mutable.value = s.copy(confirmExit = true)
        return false
    }

    fun dismissExit() = mutable.update { it.copy(confirmExit = false) }

    /** Confirmed exit: nothing is saved and the next start begins a new quiz. */
    fun confirmExit() = abandon()

    private fun abandon() {
        loadJob?.cancel()
        clearSaved()
        mutable.value = QuizUiState()
    }

    // Saved state: lets an unfinished quiz survive process death, not only rotation.
    private fun persist() {
        val s = mutable.value
        saved[KEY_ORDER] = s.questions.map { it.id }.toLongArray()
        saved[KEY_INDEX] = s.index
        saved[KEY_SELECTED] = s.answers.map { it.selectedOptionId ?: NONE }.toLongArray()
        saved[KEY_TEXTS] = ArrayList(s.answers.map { it.text })
        saved[KEY_CHECKED] = s.answers.map { it.checked }.toBooleanArray()
    }

    private fun clearSaved() = listOf(KEY_SOURCE, KEY_ORDER, KEY_INDEX, KEY_SELECTED, KEY_TEXTS, KEY_CHECKED).forEach { saved.remove<Any>(it) }

    private fun savedOrder(): LongArray? = saved.get<LongArray>(KEY_ORDER)

    /** The saved question order, or null when any saved question no longer exists. */
    private fun restored(pool: List<Question>): List<Question>? {
        val order = savedOrder()?.takeIf { it.isNotEmpty() } ?: return null
        val byId = pool.associateBy { it.id }
        return order.map { byId[it] ?: return null }
    }

    private fun restoredAnswers(picked: List<Question>): List<QuizAnswer> {
        val selected = saved.get<LongArray>(KEY_SELECTED)
        val texts = saved.get<ArrayList<String>>(KEY_TEXTS)
        val checked = saved.get<BooleanArray>(KEY_CHECKED)
        return picked.mapIndexed { i, q ->
            val option = selected?.getOrNull(i)?.takeIf { it != NONE }
            val text = texts?.getOrNull(i).orEmpty()
            val done = checked?.getOrNull(i) == true
            QuizAnswer(option, text, done, done && QuizScoring.grade(q, option, text))
        }
    }

    private fun <T> List<T>.replace(i: Int, value: T) = toMutableList().also { it[i] = value }

    companion object {
        const val QUIZ_SIZE = 10
        private const val NONE = -1L
        private const val KEY_SOURCE = "quiz_source"
        private const val KEY_ORDER = "quiz_order"
        private const val KEY_INDEX = "quiz_index"
        private const val KEY_SELECTED = "quiz_selected"
        private const val KEY_TEXTS = "quiz_texts"
        private const val KEY_CHECKED = "quiz_checked"
    }
}
