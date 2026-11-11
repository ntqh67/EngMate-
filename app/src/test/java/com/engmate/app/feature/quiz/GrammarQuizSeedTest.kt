package com.engmate.app.feature.quiz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.quiz.data.local.GrammarQuizSeed
import com.engmate.app.feature.quiz.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class GrammarQuizSeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "seed-test.db"

    @Before @After fun clean() { context.deleteDatabase(name) }

    private fun open() = Room.databaseBuilder(context, EngMateDatabase::class.java, name)
        .addCallback(GrammarQuizSeed.Callback()).allowMainThreadQueries().build()

    private fun assertSeeded(db: EngMateDatabase) = runBlocking {
        val lessons = db.grammarLessons().observeAll().first()
        assertEquals(listOf("A1", "A1", "B1"), lessons.map { it.level })
        assertTrue(lessons.all { it.examples.size in 2..3 })
        lessons.forEach { lesson ->
            val qs = db.questions().forLesson(lesson.id).map { it.toDomain() }
            assertEquals(10, qs.size)
            assertEquals(setOf(AnswerType.MULTIPLE_CHOICE, AnswerType.FILL_BLANK), qs.map { it.answerType }.toSet())
        }
        val placement = db.questions().placement(100).map { it.toDomain() }
        assertEquals(15, placement.size)
        // Every seeded question satisfies the business rules used by the admin form.
        (placement + lessons.flatMap { l -> db.questions().forLesson(l.id).map { it.toDomain() } }).forEach { q ->
            val input = QuestionInput(q.topicId, q.lessonId, q.isPlacement, q.answerType, q.content, q.explanation.orEmpty(),
                q.options.map { OptionInput(it.content, it.isCorrect) })
            assertEquals(q.content, emptyMap<QuestionField, String>(), QuestionValidator.errors(input))
            assertFalse(q.explanation.isNullOrBlank())
        }
    }

    @Test fun freshDatabaseIsSeededOnce() {
        open().also { assertSeeded(it) }.close()
        open().also { db ->
            assertSeeded(db) // reopening does not seed again
        }.close()
    }

    @Test fun upgradeFromVersionOneKeepsProfilesAndSeeds() {
        // Recreate the exact v1 schema exported by Room (schemas/…/1.json).
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { v1 ->
            v1.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `display_name` TEXT NOT NULL, `photo_url` TEXT, `english_level` TEXT NOT NULL, `daily_goal_minutes` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
            v1.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v1.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '73b0a2524ec3f2b5e530ea0fd1599f49')")
            v1.insert("user_profile", null, ContentValues().apply {
                put("uid", "alice"); put("email", "a@example.com"); put("display_name", "An"); put("english_level", "A2")
                put("daily_goal_minutes", 15); put("created_at", 1); put("updated_at", 1)
            })
            v1.version = 1
        }
        val db = open()
        try {
            assertSeeded(db)
            db.openHelper.readableDatabase.query("SELECT english_level FROM user_profile WHERE uid = 'alice'").use {
                assertTrue(it.moveToFirst()); assertEquals("A2", it.getString(0))
            }
        } finally { db.close() }
    }
}
