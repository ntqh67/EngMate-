package com.engmate.app.feature.quiz

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.contracts.EmptyTopicLookup
import com.engmate.app.core.contracts.ProgressRecorder
import com.engmate.app.core.contracts.TopicLookup
import com.engmate.app.core.contracts.TopicRef
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.auth.domain.*
import com.engmate.app.feature.grammar.data.RoomGrammarRepository
import com.engmate.app.feature.grammar.domain.*
import com.engmate.app.feature.profile.data.RoomProfileRepository
import com.engmate.app.feature.profile.domain.ProfileInput
import com.engmate.app.feature.quiz.data.RoomQuestionRepository
import com.engmate.app.feature.quiz.data.RoomQuizAttemptRepository
import com.engmate.app.feature.quiz.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RoomQuizAttemptRepository against the ProgressRecorder and TopicLookup contracts. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class IntegrationContractsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: EngMateDatabase
    private val session = object : UserSession {
        override val user = MutableStateFlow<SessionUser?>(SessionUser("alice", "alice@example.com"))
        override val isDemo = true
    }
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
    private lateinit var profiles: RoomProfileRepository
    private lateinit var grammar: RoomGrammarRepository
    private lateinit var questions: RoomQuestionRepository

    private data class Call(val uid: String, val xp: Int, val correct: Int, val total: Int, val placement: Boolean, val at: Long)

    private class FakeRecorder(private val failure: Exception? = null) : ProgressRecorder {
        val calls = mutableListOf<Call>()
        override suspend fun onQuizCompleted(uid: String, xpEarned: Int, correctCount: Int, totalQuestions: Int,
                                             isPlacement: Boolean, completedAt: Long) {
            calls += Call(uid, xpEarned, correctCount, totalQuestions, isPlacement, completedAt)
            failure?.let { throw it }
        }
    }

    private class FixedTopics(private val topics: Flow<List<TopicRef>>) : TopicLookup {
        override fun observeTopics() = topics
        override suspend fun titleOf(topicId: Long): String? = null
    }

    @Before fun setup() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(context, EngMateDatabase::class.java).allowMainThreadQueries().build()
        profiles = RoomProfileRepository(db.profiles(), session, clock)
        grammar = RoomGrammarRepository(db.grammarLessons(), clock)
        questions = RoomQuestionRepository(db.questions(), clock)
        profiles.create("alice", ProfileInput("Nguyễn An"))
    }
    @After fun close() = db.close()

    private fun attempts(recorder: ProgressRecorder = FakeRecorder(), topics: TopicLookup = EmptyTopicLookup()) =
        RoomQuizAttemptRepository(db.quizAttempts(), db.questions(), profiles, session, clock, recorder, topics)

    private fun mc(lessonId: Long? = null, topicId: Long? = null, content: String = "She ___ to school.") = QuestionInput(
        topicId = topicId, lessonId = lessonId, answerType = AnswerType.MULTIPLE_CHOICE, content = content,
        options = listOf(OptionInput("go", false), OptionInput("goes", true), OptionInput("going", false), OptionInput("gone", false)))

    private suspend fun correctOption(id: Long) = questions.observeQuestion(id).first()!!.options.first { it.isCorrect }.id
    private suspend fun wrongOption(id: Long) = questions.observeQuestion(id).first()!!.options.first { !it.isCorrect }.id

    private suspend fun lessonDraft(): AttemptDraft {
        val lessonId = grammar.create(LessonInput("Hiện tại đơn", "A1", "", "S + V(s/es)", emptyList()))
        val a = questions.create(mc(lessonId, content = "Câu 1"))
        val b = questions.create(mc(lessonId, content = "Câu 2"))
        return AttemptDraft(lessonId = lessonId, answers = listOf(
            AnswerDraft(a, selectedOptionId = correctOption(a)), AnswerDraft(b, selectedOptionId = wrongOption(b))))
    }

    @Test fun saveAttemptReportsProgressOnceWithTheSavedNumbers() = runBlocking<Unit> {
        val recorder = FakeRecorder()
        val result = attempts(recorder).saveAttempt("alice", lessonDraft())

        assertEquals(listOf(Call("alice", xp = 10, correct = 1, total = 2, placement = false, at = 5_000)), recorder.calls)
        assertEquals(result.xpEarned, recorder.calls.single().xp)
        assertEquals(result.correctCount, recorder.calls.single().correct)
    }

    @Test fun placementAttemptIsReportedAsPlacement() = runBlocking<Unit> {
        val ids = (1..QuizScoring.PLACEMENT_QUESTION_COUNT).map { questions.create(mc(content = "P$it").copy(isPlacement = true)) }
        val recorder = FakeRecorder()
        attempts(recorder).saveAttempt("alice", AttemptDraft(isPlacement = true,
            answers = ids.map { AnswerDraft(it, selectedOptionId = correctOption(it)) }))

        assertEquals(Call("alice", xp = 150, correct = 15, total = 15, placement = true, at = 5_000), recorder.calls.single())
    }

    @Test fun failingRecorderDoesNotLoseTheAttempt() = runBlocking<Unit> {
        val recorder = FakeRecorder(failure = IllegalStateException("progress down"))
        val repository = attempts(recorder)
        val result = repository.saveAttempt("alice", lessonDraft())

        assertEquals(1, recorder.calls.size)
        assertEquals(1, result.correctCount)
        assertEquals(listOf(result.attemptId), repository.observeHistory("alice").first().map { it.id })
        assertNotNull(repository.observeAttempt("alice", result.attemptId).first())
    }

    @Test fun invalidAttemptIsNotReported() = runBlocking<Unit> {
        val recorder = FakeRecorder()
        assertThrows(InvalidAttempt::class.java) {
            runBlocking { attempts(recorder).saveAttempt("alice", AttemptDraft(lessonId = 1, answers = emptyList())) }
        }
        assertTrue(recorder.calls.isEmpty())
    }

    private suspend fun topicAttempt(repository: RoomQuizAttemptRepository, topicId: Long): Long {
        val q = questions.create(mc(topicId = topicId))
        return repository.saveAttempt("alice", AttemptDraft(topicId = topicId,
            answers = listOf(AnswerDraft(q, selectedOptionId = correctOption(q))))).attemptId
    }

    @Test fun emptyTopicLookupShowsTopicNumber() = runBlocking<Unit> {
        val repository = attempts(topics = EmptyTopicLookup())
        val id = topicAttempt(repository, topicId = 7)

        assertEquals("Chủ đề #7", repository.observeHistory("alice").first().single().title)
        assertEquals("Chủ đề #7", repository.observeAttempt("alice", id).first()!!.summary.title)
    }

    @Test fun knownTopicShowsItsTitle() = runBlocking<Unit> {
        val repository = attempts(topics = FixedTopics(flowOf(listOf(TopicRef(7, "Gia đình")))))
        topicAttempt(repository, topicId = 7)
        topicAttempt(repository, topicId = 8)

        assertEquals(setOf("Gia đình", "Chủ đề #8"), repository.observeHistory("alice").first().map { it.title }.toSet())
    }

    @Test fun failingTopicLookupFallsBackToTopicNumber() = runBlocking<Unit> {
        val repository = attempts(topics = FixedTopics(flow { throw IllegalStateException("topics down") }))
        topicAttempt(repository, topicId = 7)

        assertEquals("Chủ đề #7", repository.observeHistory("alice").first().single().title)
    }
}
