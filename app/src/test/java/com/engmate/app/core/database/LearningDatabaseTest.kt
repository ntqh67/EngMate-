package com.engmate.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.vocabulary.data.local.*
import com.engmate.app.feature.notebook.data.local.*
import com.engmate.app.feature.flashcard.data.local.*
import com.engmate.app.feature.grammar.data.local.*
import com.engmate.app.feature.quiz.data.local.*
import com.engmate.app.feature.progress.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class LearningDatabaseTest {
    private lateinit var db: EngMateDatabase
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EngMateDatabase::class.java).build()
        for (uid in listOf("alice", "bob")) db.profiles().insert(UserProfileEntity(uid, "$uid@example.com", uid, null, "A1", 15, 1, 1))
    }
    @After fun close() { db.close() }
    private suspend fun word(): Long {
        val topic = db.vocabulary().insertTopic(TopicEntity(title = "Travel"))
        return db.vocabulary().insertWord(WordEntity(topicId = topic, word = "journey", meaningEn = "a trip", meaningVi = "chuyến đi"))
    }
    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("Expected constraint or validation failure") }
        catch (_: SQLiteConstraintException) { }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }
    @Test fun vocabularyCrudSearchJsonAndDynamicCount() = runBlocking {
        val dao = db.vocabulary()
        val id = word()
        val item = dao.getWord(id)!!
        assertEquals(1, dao.observeTopics().first().single().totalWords)
        assertEquals(id, dao.findWord("JOURNEY")!!.id)
        dao.updateWord(item.copy(meaningVi = "hành trình", collocations = listOf("a \"long\" journey", "hành trình", "line\nnext")))
        assertEquals(listOf("a \"long\" journey", "hành trình", "line\nnext"), dao.getWord(id)!!.collocations)
        assertEquals(1, dao.search("jour").size)
        assertTrue(dao.search("%").isEmpty())
        expectFailure { dao.insertWord(item.copy(id = 0, word = "JOURNEY")) }
        expectFailure { dao.insertWord(item.copy(id = 0, word = "other", topicId = 9999)) }
        val topic = dao.getTopic(item.topicId!!)!!
        assertEquals(1, dao.updateTopic(topic.copy(title = "Journeys")))
        assertEquals("Journeys", dao.getTopic(topic.id)!!.title)
        assertEquals(1, dao.deleteTopic(topic.id))
        assertNull(dao.getWord(id)!!.topicId)
        assertEquals(1, dao.deleteWord(id))
    }
    @Test fun notebookNotesUniqueOwnershipAndRestrictDeletes() = runBlocking {
        val id = word()
        val dao = db.notebook()
        dao.add(NotebookWordEntity(userUid = "alice", wordId = id, createdAt = 10))
        dao.add(NotebookWordEntity(userUid = "bob", wordId = id))
        expectFailure { dao.add(NotebookWordEntity(userUid = "alice", wordId = id)) }
        expectFailure { dao.add(NotebookWordEntity(userUid = "missing", wordId = id)) }
        assertEquals(1, dao.observe("alice", "jour").first().size)
        assertEquals(1, dao.updateNote("alice", id, "Ôn lại"))
        assertNull(dao.get("bob", id)!!.note)
        dao.saveWordNote(WordNoteEntity(userUid = "alice", wordId = id, customExamples = listOf("Ví dụ")))
        val note = dao.getWordNote("alice", id)!!
        dao.saveWordNote(note.copy(personalNote = "Ghi chú mới"))
        assertEquals(note.id, dao.getWordNote("alice", id)!!.id)
        assertEquals("Ghi chú mới", dao.getWordNote("alice", id)!!.personalNote)
        expectFailure { db.vocabulary().deleteWord(id) }
        expectFailure { db.profiles().delete("alice") }
        dao.remove("alice", id)
        assertNotNull(dao.get("bob", id))
        assertEquals(1, dao.deleteWordNote("alice", id))
        assertEquals(1, db.profiles().delete("alice"))
    }
    @Test fun flashcardProgressAndCollectionCascadeAreScoped() = runBlocking {
        val id = word()
        val dao = db.flashcards()
        dao.saveProgress(WordProgressEntity(userUid = "alice", wordId = id, nextReviewAt = 100))
        val old = dao.getProgress("alice", id)!!
        dao.saveProgress(old.copy(repetition = 1, nextReviewAt = 200, status = WordLearningStatus.REVIEW))
        assertEquals(old.id, dao.getProgress("alice", id)!!.id)
        assertTrue(dao.observeDue("alice", 199).first().isEmpty())
        assertEquals(1, dao.observeDue("alice", 200).first().size)
        assertTrue(dao.observeDue("bob", 300).first().isEmpty())
        expectFailure { dao.saveProgress(old.copy(easeFactor = Double.NaN)) }
        val collection = dao.insertCollection(FlashcardCollectionEntity(userUid = "alice", title = "My deck"))
        val card = dao.addCard("alice", FlashcardEntity(collectionId = collection, word = "hello", meaning = "xin chào"))
        expectFailure { dao.addCard("bob", FlashcardEntity(collectionId = collection, word = "a", meaning = "b")) }
        assertEquals(0, dao.updateCard("bob", card, "x", "y", null, null))
        assertEquals(1, dao.updateCard("alice", card, "hi", "chào", null, null))
        assertEquals("hi", dao.observeCards("alice", collection).first().single().word)
        assertEquals(1, dao.updateCollection("alice", collection, "Updated", null))
        assertEquals(0, dao.deleteCollection("bob", collection))
        assertEquals(1, dao.deleteCollection("alice", collection))
        assertTrue(dao.observeCards("alice", collection).first().isEmpty())
        db.openHelper.readableDatabase.query("SELECT count(*) FROM flashcards").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        assertEquals(1, dao.deleteProgress("alice", id))
    }
    @Test fun grammarCrudAndQuestionOptionsAreAtomic() = runBlocking {
        val grammar = db.grammar()
        val lesson = grammar.insert(GrammarLessonEntity(title = "Present simple", level = "A1", content = "Rules", examples = listOf("I work.")))
        grammar.update(grammar.get(lesson)!!.copy(content = "New rules"))
        assertEquals("New rules", grammar.observeLessons("A1").first().single().content)
        assertTrue(grammar.observeLessons("B2").first().isEmpty())
        val quiz = db.quiz()
        val id = quiz.saveQuestion(QuestionEntity(lessonId = lesson, content = "I ... a student."), listOf(
            QuestionOptionEntity(questionId = 0, content = "am", isCorrect = true),
            QuestionOptionEntity(questionId = 0, content = "are")))
        assertEquals(2, quiz.getQuestion(id)!!.options.size)
        expectFailure { grammar.delete(lesson) }
        val old = quiz.getQuestion(id)!!
        expectFailure { quiz.saveQuestion(old.question.copy(content = "Bad edit"), emptyList()) }
        assertEquals("I ... a student.", quiz.getQuestion(id)!!.question.content)
        quiz.saveQuestion(old.question.copy(content = "Updated question"), old.options)
        assertEquals("Updated question", quiz.getQuestion(id)!!.question.content)
        quiz.deleteQuestion(id)
        db.openHelper.readableDatabase.query("SELECT count(*) FROM question_options").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        assertEquals(1, grammar.delete(lesson))
    }
    @Test fun quizAttemptOwnershipValidationSnapshotAndCascade() = runBlocking {
        val quiz = db.quiz()
        val qid = quiz.saveQuestion(QuestionEntity(isPlacement = true, content = "Choose hello"), listOf(
            QuestionOptionEntity(questionId = 0, content = "hello", isCorrect = true),
            QuestionOptionEntity(questionId = 0, content = "goodbye")))
        val question = quiz.getQuestion(qid)!!
        val option = question.options.single { it.isCorrect }
        val attempt = QuizAttemptEntity(userUid = "alice", isPlacement = true, correctCount = 1, totalQuestions = 1, completedAt = 100, xpEarned = 10)
        val answer = AttemptAnswerEntity(attemptId = 0, questionId = qid, selectedOptionId = option.id, isCorrect = true)
        expectFailure { quiz.saveAttempt(attempt, listOf(answer.copy(selectedOptionId = 999))) }
        assertTrue(quiz.observeAttempts("alice").first().isEmpty())
        val id = quiz.saveAttempt(attempt, listOf(answer))
        assertNull(quiz.getAttempt("bob", id))
        assertEquals("Choose hello", quiz.getAttempt("alice", id)!!.answers.single().questionSnapshot)
        assertEquals("hello", quiz.getAttempt("alice", id)!!.answers.single().selectedOptionSnapshot)
        val otherQuestion = quiz.saveQuestion(QuestionEntity(isPlacement = true, content = "Other question"), listOf(
            QuestionOptionEntity(questionId = 0, content = "one", isCorrect = true),
            QuestionOptionEntity(questionId = 0, content = "two")))
        // Even raw SQL cannot associate an option with a different question.
        expectFailure {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO attempt_answers (attempt_id, question_id, selected_option_id, question_snapshot, is_correct) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(id, otherQuestion, option.id, "Other question", 0))
        }
        assertEquals(1, quiz.getAttempt("alice", id)!!.answers.size)

        expectFailure { quiz.deleteQuestion(qid) }
        // Removal of referenced options fails, rolling back the question text update too.
        expectFailure { quiz.saveQuestion(question.question.copy(content = "Changed"), question.options) }
        assertEquals("Choose hello", quiz.getQuestion(qid)!!.question.content)
        assertEquals(0, quiz.deleteAttempt("bob", id))
        assertEquals(1, quiz.deleteAttempt("alice", id))
        db.openHelper.readableDatabase.query("SELECT count(*) FROM attempt_answers").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        assertEquals(1, quiz.deleteQuestion(qid))
    }
    @Test fun textQuestionsRequireExpectedAndSubmittedAnswers() = runBlocking {
        val dao = db.quiz()
        expectFailure { dao.saveQuestion(QuestionEntity(isPlacement = true, answerType = AnswerType.TEXT, content = "Fill"), emptyList()) }
        val qid = dao.saveQuestion(QuestionEntity(isPlacement = true, answerType = AnswerType.TEXT, content = "Fill", correctAnswer = "am"), emptyList())
        val attempt = QuizAttemptEntity(userUid = "alice", isPlacement = true, correctCount = 1, totalQuestions = 1, completedAt = 1)
        expectFailure { dao.saveAttempt(attempt, listOf(AttemptAnswerEntity(attemptId = 0, questionId = qid, isCorrect = true))) }
        val id = dao.saveAttempt(attempt, listOf(AttemptAnswerEntity(attemptId = 0, questionId = qid, answerText = "am", isCorrect = true)))
        assertEquals("am", dao.getAttempt("alice", id)!!.answers.single().answerText)
    }
    private fun activity(id: String, date: String = "2026-11-12") = LearningActivityEntity(
        activityId = id, userUid = "alice", activityType = ActivityType.FLASHCARD,
        xpEarned = 20, durationSeconds = 300, occurredAt = 1, studyDate = date, wordsReviewed = 10)
    @Test fun activityCrudRebuildsBothDaysAndPreventsDuplicateXp() = runBlocking {
        val dao = db.study()
        val first = activity("session-1")
        assertTrue(dao.record(first))
        assertFalse(dao.record(first))
        assertEquals(20L, dao.getStat("alice", first.studyDate)!!.xpEarned)
        expectFailure { dao.record(first.copy(xpEarned = 999)) }
        dao.record(activity("session-2"))
        assertEquals(40L, dao.getStat("alice", first.studyDate)!!.xpEarned)
        assertFalse(dao.delete("bob", first.activityId))
        assertTrue(dao.revise("alice", first.copy(studyDate = "2026-11-13", xpEarned = 5)))
        assertEquals(20L, dao.getStat("alice", "2026-11-12")!!.xpEarned)
        assertEquals(5L, dao.getStat("alice", "2026-11-13")!!.xpEarned)
        assertTrue(dao.observeStats("bob", "2026-11-01", "2026-11-30").first().isEmpty())
        assertEquals(2, dao.observeActivities("alice", "2026-11-01", "2026-11-30").first().size)
        dao.delete("alice", first.activityId)
        assertNull(dao.getStat("alice", "2026-11-13"))
        expectFailure { dao.record(activity("invalid", "2026-02-30")) }
        assertNull(dao.getActivity("alice", "invalid"))
    }
    @Test fun failedStatWriteRollsBackActivity() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_stats BEFORE INSERT ON daily_study_stat BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        expectFailure { db.study().record(activity("rollback")) }
        assertNull(db.study().getActivity("alice", "rollback"))
        assertNull(db.study().getStat("alice", "2026-11-12"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_stats")
        assertTrue(db.study().record(activity("rollback")))
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }
}
