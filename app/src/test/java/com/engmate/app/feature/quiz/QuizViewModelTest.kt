package com.engmate.app.feature.quiz

import androidx.lifecycle.SavedStateHandle
import com.engmate.app.core.contracts.EmptyTopicLookup
import com.engmate.app.core.contracts.TopicLookup
import com.engmate.app.core.contracts.TopicRef
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.grammar.domain.LessonInput
import com.engmate.app.feature.quiz.domain.*
import com.engmate.app.feature.quiz.presentation.quiz.QuizSaveError
import com.engmate.app.feature.quiz.presentation.quiz.QuizViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
    private val session = object : UserSession {
        override val user = this@QuizViewModelTest.user
        override val isDemo = true
    }

    private class FakeQuestions(
        val lesson: List<Question> = emptyList(),
        val topic: List<Question> = emptyList(),
        val placement: List<Question> = emptyList(),
        var failLoad: Boolean = false,
    ) : QuestionRepository {
        override fun observeQuestions(): Flow<List<Question>> = flowOf(lesson + topic + placement)
        override fun observeQuestion(id: Long): Flow<Question?> = flowOf((lesson + topic + placement).firstOrNull { it.id == id })
        override suspend fun questionsForLesson(lessonId: Long) = if (failLoad) error("disk") else lesson
        override suspend fun questionsForTopic(topicId: Long) = if (failLoad) error("disk") else topic
        override suspend fun placementQuestions() = if (failLoad) error("disk") else placement
        override suspend fun create(input: QuestionInput): Long = error("unused")
        override suspend fun update(id: Long, input: QuestionInput) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
    }

    private class FakeAttempts(var failure: Exception? = null) : QuizAttemptRepository {
        val saved = mutableListOf<AttemptDraft>()
        override fun observeHistory(uid: String): Flow<List<AttemptSummary>> = flowOf(emptyList())
        override fun observeAttempt(uid: String, id: Long): Flow<AttemptDetail?> = flowOf(null)
        override suspend fun saveAttempt(uid: String, draft: AttemptDraft): AttemptResult {
            failure?.let { throw it }
            saved += draft
            val correct = draft.answers.size
            return AttemptResult(saved.size.toLong(), correct, draft.answers.size, correct * 10,
                if (draft.isPlacement) "B2" else null, levelSaved = draft.isPlacement)
        }
        override suspend fun delete(uid: String, id: Long) = Unit
    }

    private val grammar = object : GrammarRepository {
        override fun observeLessons(): Flow<List<GrammarLesson>> = flowOf(emptyList())
        override fun observeLesson(id: Long): Flow<GrammarLesson?> = flowOf(null)
        override suspend fun create(input: LessonInput): Long = error("unused")
        override suspend fun update(id: Long, input: LessonInput) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
    }

    /** Multiple choice: options id*10+1..4, the second one is correct. */
    private fun mc(id: Long, lessonId: Long? = 1, topicId: Long? = null, placement: Boolean = false) = Question(
        id, topicId, lessonId.takeIf { topicId == null && !placement }, placement, AnswerType.MULTIPLE_CHOICE, "Q$id", "giải thích",
        listOf("go", "goes", "going", "gone").mapIndexed { i, c -> QuestionOption(id * 10 + i + 1, c, i == 1, i) }, null)

    private fun fb(id: Long, lessonId: Long = 1) = Question(id, null, lessonId, false, AnswerType.FILL_BLANK, "He ___ (go).", null,
        listOf(QuestionOption(id * 10 + 1, "goes", true, 0)), null)

    private fun vm(questions: QuestionRepository, attempts: QuizAttemptRepository = FakeAttempts(),
                   topics: TopicLookup = EmptyTopicLookup(), saved: SavedStateHandle = SavedStateHandle()) =
        QuizViewModel(questions, attempts, grammar, topics, session, saved)

    /** Answers the current question correctly (or wrongly), checks it and moves on. */
    private fun TestScope.answer(vm: QuizViewModel, correct: Boolean = true) {
        val q = vm.state.value.current!!
        when (q.answerType) {
            AnswerType.MULTIPLE_CHOICE -> vm.select(q.options.first { it.isCorrect == correct }.id)
            AnswerType.FILL_BLANK -> vm.type(if (correct) "goes" else "go")
        }
        vm.check(); vm.next(); runCurrent()
    }

    @Test fun selectingAnOptionKeepsOnlyThatOne() = runTest(dispatcher) {
        val vm = vm(FakeQuestions(lesson = listOf(mc(1))))
        vm.start(QuizSource.Lesson(1)); runCurrent()
        vm.select(11); vm.select(13)
        assertEquals(13L, vm.state.value.currentAnswer.selectedOptionId)
        vm.select(999) // not an option of this question
        assertEquals(13L, vm.state.value.currentAnswer.selectedOptionId)
    }

    @Test fun checkIsDisabledWithoutAnAnswerIncludingBlankText() = runTest(dispatcher) {
        val vm = vm(FakeQuestions(lesson = listOf(mc(1), fb(2))))
        vm.start(QuizSource.Lesson(1)); runCurrent()
        // Shuffled order: find each type by moving through.
        repeat(2) {
            val q = vm.state.value.current!!
            assertFalse(vm.state.value.canCheck)
            vm.check()
            assertFalse(vm.state.value.currentAnswer.checked)
            if (q.answerType == AnswerType.FILL_BLANK) {
                vm.type("   ")
                assertFalse(vm.state.value.canCheck)
                vm.check()
                assertFalse(vm.state.value.currentAnswer.checked)
                vm.type(" goes ")
            } else vm.select(q.options.first().id)
            assertTrue(vm.state.value.canCheck)
            vm.check(); vm.next(); runCurrent()
        }
    }

    @Test fun gradesBothQuestionTypesAndLocksTheAnswer() = runTest(dispatcher) {
        val vm = vm(FakeQuestions(lesson = listOf(mc(1), fb(2))))
        vm.start(QuizSource.Lesson(1)); runCurrent()
        repeat(2) {
            val q = vm.state.value.current!!
            if (q.answerType == AnswerType.MULTIPLE_CHOICE) {
                vm.select(q.options.first { !it.isCorrect }.id); vm.check()
                assertFalse(vm.state.value.currentAnswer.correct)
                vm.select(q.options.first { it.isCorrect }.id) // locked after checking
                assertFalse(vm.state.value.currentAnswer.correct)
                assertEquals(q.options.first { !it.isCorrect }.id, vm.state.value.currentAnswer.selectedOptionId)
            } else {
                vm.type("  GOES "); vm.check()
                assertTrue(vm.state.value.currentAnswer.correct)
            }
            assertTrue(vm.state.value.currentAnswer.checked)
            vm.next(); runCurrent()
        }
    }

    @Test fun lessonTakesAtMostTenDistinctQuestions() = runTest(dispatcher) {
        val pool = (1L..15L).map { mc(it) }
        val vm = vm(FakeQuestions(lesson = pool))
        vm.start(QuizSource.Lesson(1)); runCurrent()
        val picked = vm.state.value.questions
        assertEquals(10, picked.size)
        assertEquals(10, picked.map { it.id }.toSet().size)
        assertTrue(pool.containsAll(picked))
    }

    @Test fun emptyLessonShowsEmptyState() = runTest(dispatcher) {
        val vm = vm(FakeQuestions())
        vm.start(QuizSource.Lesson(1)); runCurrent()
        assertEquals("Bài này chưa có câu hỏi", vm.state.value.emptyMessage)
        assertTrue(vm.state.value.questions.isEmpty())
    }

    @Test fun topicNeedsTenQuestionsAndUsesTheTopicTitle() = runTest(dispatcher) {
        val few = vm(FakeQuestions(topic = (1L..9L).map { mc(it, topicId = 5) }))
        few.start(QuizSource.Topic(5)); runCurrent()
        assertEquals("Chủ đề cần ít nhất 10 câu hỏi để làm quiz", few.state.value.emptyMessage)
        assertEquals("Chủ đề #5", few.state.value.title)

        val topics = object : TopicLookup {
            override fun observeTopics() = flowOf(listOf(TopicRef(5, "Du lịch")))
            override suspend fun titleOf(topicId: Long) = "Du lịch"
        }
        val enough = vm(FakeQuestions(topic = (1L..12L).map { mc(it, topicId = 5) }), topics = topics)
        enough.start(QuizSource.Topic(5)); runCurrent()
        assertEquals(10, enough.state.value.questions.size)
        assertEquals("Du lịch", enough.state.value.title)
    }

    @Test fun placementNeedsExactlyFifteenInFixedOrder() = runTest(dispatcher) {
        val pool = (1L..15L).map { mc(it, placement = true) }
        val vm = vm(FakeQuestions(placement = pool))
        vm.start(QuizSource.Placement); runCurrent()
        assertEquals(pool.map { it.id }, vm.state.value.questions.map { it.id })
        assertEquals(PLACEMENT_TITLE, vm.state.value.title)

        val short = vm(FakeQuestions(placement = pool.take(14)))
        short.start(QuizSource.Placement); runCurrent()
        assertNotNull(short.state.value.emptyMessage)
        assertTrue(short.state.value.emptyMessage!!.contains("15"))
    }

    @Test fun loadFailureOffersRetry() = runTest(dispatcher) {
        val questions = FakeQuestions(lesson = listOf(mc(1)), failLoad = true)
        val vm = vm(questions)
        vm.start(QuizSource.Lesson(1)); runCurrent()
        assertNotNull(vm.state.value.loadError)
        questions.failLoad = false
        vm.retryLoad(); runCurrent()
        assertNull(vm.state.value.loadError)
        assertEquals(1, vm.state.value.questions.size)
    }

    @Test fun finishingSavesEveryAnswerWithTheSource() = runTest(dispatcher) {
        val attempts = FakeAttempts()
        val vm = vm(FakeQuestions(lesson = listOf(mc(1), fb(2))), attempts)
        vm.start(QuizSource.Lesson(7)); runCurrent()
        // Order is shuffled: answer the choice question right and the fill-blank one wrong, whichever comes first.
        repeat(2) { answer(vm, correct = vm.state.value.current!!.answerType == AnswerType.MULTIPLE_CHOICE) }

        val draft = attempts.saved.single()
        assertEquals(7L, draft.lessonId); assertNull(draft.topicId); assertFalse(draft.isPlacement)
        assertEquals(setOf(1L, 2L), draft.answers.map { it.questionId }.toSet())
        assertEquals("go", draft.answers.first { it.questionId == 2L }.answerText)
        assertEquals(1L, vm.state.value.result?.attemptId)
    }

    @Test fun exitingMidwayAsksAndSavesNothing() = runTest(dispatcher) {
        val attempts = FakeAttempts()
        val vm = vm(FakeQuestions(lesson = listOf(mc(1), mc(2))), attempts)
        vm.start(QuizSource.Lesson(1)); runCurrent()
        assertTrue("no answer yet: leave right away", vm.requestExit())

        vm.start(QuizSource.Lesson(1)); runCurrent()
        answer(vm)
        assertFalse(vm.requestExit())
        assertTrue(vm.state.value.confirmExit)
        vm.dismissExit()
        assertFalse(vm.state.value.confirmExit)
        assertFalse(vm.requestExit())
        vm.confirmExit(); runCurrent()
        assertTrue(attempts.saved.isEmpty())

        // The next start is a fresh quiz.
        vm.start(QuizSource.Lesson(1)); runCurrent()
        assertEquals(0, vm.state.value.index)
        assertTrue(vm.state.value.answers.none { it.checked })
    }

    @Test fun missingProfileKeepsAnswersForRetry() = runTest(dispatcher) {
        val attempts = FakeAttempts(failure = ProfileRequired())
        val vm = vm(FakeQuestions(lesson = listOf(mc(1))), attempts)
        vm.start(QuizSource.Lesson(1)); runCurrent()
        answer(vm)
        assertEquals(QuizSaveError.ProfileRequired, vm.state.value.saveError)
        assertTrue(vm.state.value.answers.single().checked)
        assertNull(vm.state.value.result)

        attempts.failure = null
        vm.retrySave(); runCurrent()
        assertNull(vm.state.value.saveError)
        assertNotNull(vm.state.value.result)
    }

    @Test fun signedOutOrSessionMismatchAsksToSignIn() = runTest(dispatcher) {
        val signedOut = vm(FakeQuestions(lesson = listOf(mc(1))))
        signedOut.start(QuizSource.Lesson(1)); runCurrent()
        user.value = null
        answer(signedOut)
        assertEquals(QuizSaveError.SignInRequired, signedOut.state.value.saveError)

        user.value = SessionUser("alice", "alice@example.com")
        val mismatch = vm(FakeQuestions(lesson = listOf(mc(1))), FakeAttempts(failure = AttemptSessionRequired()))
        mismatch.start(QuizSource.Lesson(1)); runCurrent()
        answer(mismatch)
        assertEquals(QuizSaveError.SignInRequired, mismatch.state.value.saveError)
    }

    @Test fun invalidAttemptShowsItsMessage() = runTest(dispatcher) {
        val vm = vm(FakeQuestions(lesson = listOf(mc(1))), FakeAttempts(failure = InvalidAttempt("Câu hỏi #1 không còn tồn tại")))
        vm.start(QuizSource.Lesson(1)); runCurrent()
        answer(vm)
        assertEquals(QuizSaveError.Invalid("Câu hỏi #1 không còn tồn tại"), vm.state.value.saveError)
    }

    @Test fun progressSurvivesProcessDeath() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val questions = FakeQuestions(lesson = (1L..5L).map { mc(it) })
        val first = vm(questions, saved = saved)
        first.start(QuizSource.Lesson(1)); runCurrent()
        answer(first)
        val pending = first.state.value.current!!
        first.select(pending.options[2].id)
        val order = first.state.value.questions.map { it.id }

        val restored = vm(questions, saved = saved)
        restored.start(QuizSource.Lesson(1)); runCurrent()
        assertEquals(order, restored.state.value.questions.map { it.id })
        assertEquals(1, restored.state.value.index)
        assertTrue(restored.state.value.answers[0].checked && restored.state.value.answers[0].correct)
        assertEquals(pending.options[2].id, restored.state.value.currentAnswer.selectedOptionId)
    }

    @Test fun retryAfterFinishingStartsOver() = runTest(dispatcher) {
        val vm = vm(FakeQuestions(lesson = listOf(mc(1))))
        vm.start(QuizSource.Lesson(1)); runCurrent()
        answer(vm)
        assertNotNull(vm.state.value.result)
        vm.start(QuizSource.Lesson(1)); runCurrent()
        assertNull(vm.state.value.result)
        assertFalse(vm.state.value.currentAnswer.checked)
    }
}
