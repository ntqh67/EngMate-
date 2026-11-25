package com.engmate.app.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.grammar.domain.LessonInput
import com.engmate.app.feature.grammar.domain.LessonValidator
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.quiz.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GrammarBaselineOfflineTest {
    @Test fun unknownLevelAndEnglishOnlyExamplesDoNotRequireInventedMetadata() {
        assertTrue(LessonValidator.errors(LessonInput("Source lesson", LessonValidator.UNKNOWN_LEVEL,
            content = "Source prose", examples = listOf(GrammarExample("Source example.", "")))).isEmpty())
    }

    @Test fun baselineSeedsPreserveHistoryAndRemainAvailableOffline() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "grammar-open-seed-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, EngMateDatabase::class.java, name)
            .addCallback(GrammarQuizSeed.Callback()).allowMainThreadQueries().build()
        var db = open()
        try {
            val originalLessons = db.grammarLessons().all()
            val originalQuestions = db.questions().all()
            assertEquals(3, originalLessons.size)
            db.profiles().insert(UserProfileEntity("learner", "learner@example.test", "Learner", null, "A1", 15, 1, 1))
            val original = originalQuestions.first()
            val selected = original.options.first()
            val oldAttempt = db.quizAttempts().saveAttempt(
                QuizAttemptEntity(userUid = "learner", lessonId = original.question.lessonId,
                    isPlacement = original.question.isPlacement, totalQuestions = 1, completedAt = 1),
                listOf(AttemptAnswerEntity(attemptId = 0, questionId = original.question.id,
                    selectedOptionId = selected.id, questionSnapshot = original.question.content)))
            suspend fun seed() = ContentSeeder(context, db.grammarLessons(), db.questions(), db).seedRoom()
            seed()
            assertEquals(17, db.grammarLessons().all().size)
            assertEquals(originalLessons, db.grammarLessons().all().filter { it.id in originalLessons.map { l -> l.id } })
            originalQuestions.forEach { assertEquals(it, db.questions().get(it.question.id)) }
            val before = db.questions().all()
            val custom = db.grammarLessons().all().first { it.id == 101L }.copy(title = "Existing local edit")
            db.grammarLessons().upsert(custom)
            seed()
            assertEquals(before, db.questions().all()) // option ids stay intact
            assertEquals(custom, db.grammarLessons().get(101))
            assertEquals(1, db.quizAttempts().answerCount(oldAttempt))
            db.close()
            db = open() // no Firestore dependency: content and history remain on disk
            assertEquals(17, db.grammarLessons().observeAll().first().size)
            assertEquals(1, db.quizAttempts().observeEntities("learner").first().size)
            assertTrue(db.quizAttempts().observeEntities("other-uid").first().isEmpty())
            assertEquals(before, db.questions().all())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
