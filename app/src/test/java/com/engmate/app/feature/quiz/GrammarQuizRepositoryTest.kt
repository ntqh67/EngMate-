package com.engmate.app.feature.quiz

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Room in-memory tests for DAOs + repositories (Robolectric, same approach as the profile tests). */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class GrammarQuizRepositoryTest {
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
    private lateinit var attempts: RoomQuizAttemptRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, EngMateDatabase::class.java).allowMainThreadQueries().build()
        profiles = RoomProfileRepository(db.profiles(), session, clock)
        grammar = RoomGrammarRepository(db.grammarLessons(), clock)
        questions = RoomQuestionRepository(db.questions(), clock)
        attempts = RoomQuizAttemptRepository(db.quizAttempts(), db.questions(), profiles, session, clock)
    }
    @After fun close() = db.close()

    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private suspend fun lesson(title: String = "Hiện tại đơn") = grammar.create(LessonInput(title, "A1", "", "S + V(s/es)",
        listOf(GrammarExample("I go.", "Tôi đi."))))

    private fun mcInput(lessonId: Long?, content: String = "She ___ to school.", placement: Boolean = false) = QuestionInput(
        lessonId = lessonId, isPlacement = placement, answerType = AnswerType.MULTIPLE_CHOICE, content = content, explanation = "ngôi 3",
        options = listOf(OptionInput("go", false), OptionInput("goes", true), OptionInput("going", false), OptionInput("gone", false)))

    private fun fbInput(lessonId: Long, vararg accepted: String) = QuestionInput(lessonId = lessonId,
        answerType = AnswerType.FILL_BLANK, content = "He ___ (watch) TV.", options = accepted.map { OptionInput(it, true) })

    @Test fun lessonCrudKeepsExamplesJson() = runBlocking<Unit> {
        val id = lesson()
        assertEquals(listOf(GrammarExample("I go.", "Tôi đi.")), grammar.observeLesson(id).first()!!.examples)
        grammar.update(id, LessonInput("Hiện tại đơn (sửa)", "A2", "Tóm tắt", "Nội dung", emptyList()))
        val updated = grammar.observeLesson(id).first()!!
        assertEquals("Hiện tại đơn (sửa)", updated.title); assertEquals("A2", updated.level); assertTrue(updated.examples.isEmpty())
        try { grammar.create(LessonInput("A", "C1", "", "")); fail() } catch (e: InvalidLesson) {
            assertEquals(setOf(LessonField.TITLE, LessonField.LEVEL, LessonField.CONTENT), e.errors.keys)
        }
    }

    @Test fun questionCreateUpdateDeleteWithOptions() = runBlocking<Unit> {
        val lessonId = lesson()
        val id = questions.create(mcInput(lessonId))
        val created = questions.observeQuestion(id).first()!!
        assertEquals(listOf("go", "goes", "going", "gone"), created.options.map { it.content })
        assertEquals("goes", created.correctOptions.single().content)
        assertEquals(QuestionCategory.GRAMMAR, created.category)

        questions.update(id, fbInput(lessonId, "watches", "is watching"))
        val updated = questions.observeQuestion(id).first()!!
        assertEquals(AnswerType.FILL_BLANK, updated.answerType)
        assertEquals(listOf("watches", "is watching"), updated.options.map { it.content })
        assertTrue(updated.options.all { it.isCorrect })
        assertEquals("options are replaced, not appended", 2, count("question_options"))

        questions.delete(id)
        assertNull(questions.observeQuestion(id).first())
        assertEquals(0, count("question_options"))
    }

    @Test fun invalidQuestionIsNotSaved() = runBlocking<Unit> {
        try { questions.create(mcInput(null)); fail() } catch (e: InvalidQuestion) { assertTrue(QuestionField.CATEGORY in e.errors) }
        assertEquals(0, count("questions"))
    }

    @Test fun deletingQuestionCascadesOptions() = runBlocking<Unit> {
        val id = questions.create(mcInput(lesson()))
        assertEquals(4, count("question_options"))
        db.questions().delete(id)
        assertEquals(0, count("question_options"))
    }

    @Test fun answeredQuestionCannotBeEditedOrDeleted() = runBlocking<Unit> {
        profiles.create("alice", ProfileInput("Nguyễn An"))
        val lessonId = lesson()
        val id = questions.create(mcInput(lessonId))
        val goes = questions.observeQuestion(id).first()!!.correctOptions.single().id
        attempts.saveAttempt("alice", AttemptDraft(lessonId = lessonId, answers = listOf(AnswerDraft(id, selectedOptionId = goes))))

        assertThrows(QuestionInUse::class.java) { runBlocking { questions.update(id, mcInput(lessonId, "Sửa")) } }
        assertThrows(QuestionInUse::class.java) { runBlocking { questions.delete(id) } }
        // The database enforces it as well (RESTRICT).
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { db.questions().delete(id) } }
        assertEquals("She ___ to school.", questions.observeQuestion(id).first()!!.content)
        assertThrows(LessonInUse::class.java) { runBlocking { grammar.delete(lessonId) } }
    }

    @Test fun deletingUnusedLessonDeletesItsQuestions() = runBlocking<Unit> {
        val lessonId = lesson()
        val other = lesson("Khác")
        questions.create(mcInput(lessonId)); questions.create(fbInput(lessonId, "watches")); questions.create(mcInput(other))
        grammar.delete(lessonId)
        assertNull(grammar.observeLesson(lessonId).first())
        assertEquals(1, count("questions"))
        assertEquals(4, count("question_options"))
        assertThrows(LessonMissing::class.java) { runBlocking { grammar.delete(lessonId) } }
    }

    @Test fun saveAndReadAttemptWithAnswers() = runBlocking<Unit> {
        profiles.create("alice", ProfileInput("Nguyễn An"))
        val lessonId = lesson()
        val mc = questions.create(mcInput(lessonId))
        val fb = questions.create(fbInput(lessonId, "watches"))
        val wrong = questions.observeQuestion(mc).first()!!.options.first { !it.isCorrect }.id

        val result = attempts.saveAttempt("alice", AttemptDraft(lessonId = lessonId, answers = listOf(
            AnswerDraft(mc, selectedOptionId = wrong), AnswerDraft(fb, answerText = "  WATCHES "))))
        assertEquals(1, result.correctCount); assertEquals(2, result.totalQuestions); assertEquals(10, result.xpEarned)
        assertNull(result.level)

        val row = db.quizAttempts().observeWithAnswers("alice", result.attemptId).first()!!
        assertEquals("Hiện tại đơn", row.lessonTitle)
        assertEquals(2, row.answers.size)
        assertEquals(4, row.answers.first { it.answer.questionId == mc }.question.options.size)

        val detail = attempts.observeAttempt("alice", result.attemptId).first()!!
        assertEquals("Hiện tại đơn", detail.summary.title)
        val (first, second) = detail.answers
        assertEquals("go", first.given); assertEquals(listOf("goes"), first.correctAnswers); assertFalse(first.isCorrect)
        assertEquals("WATCHES", second.given); assertTrue(second.isCorrect)

        val history = attempts.observeHistory("alice").first()
        assertEquals(listOf(result.attemptId), history.map { it.id })
        assertEquals(5_000, history.single().completedAt)
    }

    @Test fun deletingAttemptCascadesAnswers() = runBlocking<Unit> {
        profiles.create("alice", ProfileInput("Nguyễn An"))
        val lessonId = lesson()
        val fb = questions.create(fbInput(lessonId, "watches"))
        val id = attempts.saveAttempt("alice", AttemptDraft(lessonId = lessonId, answers = listOf(AnswerDraft(fb, answerText = "x")))).attemptId
        assertEquals(1, db.quizAttempts().answerCount(id))
        attempts.delete("alice", id)
        assertEquals(0, count("attempt_answers"))
        assertTrue(attempts.observeHistory("alice").first().isEmpty())
        assertThrows(AttemptMissing::class.java) { runBlocking { attempts.delete("alice", id) } }
        questions.delete(fb) // no longer referenced, so it can be deleted again
    }

    @Test fun placementAttemptUpdatesProfileLevel() = runBlocking<Unit> {
        profiles.create("alice", ProfileInput("Nguyễn An", englishLevel = "A1"))
        val ids = (1..15).map { questions.create(mcInput(null, "Placement $it", placement = true)) }
        val placement = questions.placementQuestions()
        assertEquals(ids, placement.map { it.id })
        val answers = placement.mapIndexed { i, q ->
            AnswerDraft(q.id, selectedOptionId = (if (i < 9) q.correctOptions else q.options.filterNot { it.isCorrect }).first().id)
        }
        val result = attempts.saveAttempt("alice", AttemptDraft(isPlacement = true, answers = answers))
        assertEquals(9, result.correctCount); assertEquals("B1", result.level); assertTrue(result.levelSaved)
        assertEquals("B1", profiles.observe("alice").first()!!.englishLevel)
        assertEquals(PLACEMENT_TITLE, attempts.observeHistory("alice").first().single().title)

        assertThrows(InvalidAttempt::class.java) { runBlocking { attempts.saveAttempt("alice", AttemptDraft(isPlacement = true, answers = answers.take(14))) } }
    }

    @Test fun attemptRequiresProfileAndMatchingSession() = runBlocking<Unit> {
        val lessonId = lesson()
        val fb = questions.create(fbInput(lessonId, "watches"))
        val draft = AttemptDraft(lessonId = lessonId, answers = listOf(AnswerDraft(fb, answerText = "watches")))
        assertThrows(ProfileRequired::class.java) { runBlocking { attempts.saveAttempt("alice", draft) } }
        assertThrows(AttemptSessionRequired::class.java) { runBlocking { attempts.saveAttempt("bob", draft) } }
        assertEquals(0, count("quiz_attempts"))
    }

    @Test fun deletingProfileRemovesItsHistory() = runBlocking<Unit> {
        profiles.create("alice", ProfileInput("Nguyễn An"))
        val lessonId = lesson()
        val fb = questions.create(fbInput(lessonId, "watches"))
        attempts.saveAttempt("alice", AttemptDraft(lessonId = lessonId, answers = listOf(AnswerDraft(fb, answerText = "watches"))))
        profiles.delete("alice")
        assertEquals(0, count("quiz_attempts"))
        assertEquals(0, count("attempt_answers"))
    }
}
