package com.engmate.app.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.feature.vocabulary.data.local.TopicEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class DatabaseMigrationTest {
    @Test fun versionOneUpgradesWithoutLosingProfile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v1-v2.db"
        context.deleteDatabase(name)
        val schema = javaClass.classLoader!!.getResourceAsStream("com.engmate.app.core.database.EngMateDatabase/1.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) sqlite.execSQL(queries.getString(i))
            sqlite.execSQL("INSERT INTO user_profile VALUES (?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>("legacy", "old@example.com", "Nguyễn An", null, "B1", 30, 100, 200))
            sqlite.version = 1
        }
        val db = Room.databaseBuilder(context, EngMateDatabase::class.java, name).addMigrations(MergeDatabaseMigration).build()
        try {
            val profile = db.profiles().observe("legacy").first()!!
            assertEquals("Nguyễn An", profile.displayName)
            assertEquals(30, profile.dailyGoalMinutes)
            assertEquals(100L, profile.createdAt)
            assertEquals(200L, profile.updatedAt)
            db.vocabulary().insertTopic(TopicEntity(title = "Migrated"))
            assertEquals(1, db.vocabulary().observeTopics().first().size)
            db.openHelper.readableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(3, it.getInt(0)) }
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun versionTwoPreservesRowsWhenRenamingColumns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v2-v3.db"
        context.deleteDatabase(name)
        val schema = javaClass.classLoader!!.getResourceAsStream("com.engmate.app.core.database.EngMateDatabase/2.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) sqlite.execSQL(queries.getString(i))
            sqlite.execSQL("INSERT INTO user_profile (uid,email,display_name,english_level,daily_goal_minutes,created_at,updated_at) VALUES ('alice','a@example.com','An Nguyen','A1',15,1,1)")
            sqlite.execSQL("INSERT INTO words (id,word,meaning_en,created_at,collocations,idioms) VALUES (1,'hello','greeting',1,'[]','[]')")
            sqlite.execSQL("INSERT INTO word_progress (id,user_uid,word_id,interval_days,ease_factor,next_review_at,status) VALUES (1,'alice',1,7,2.5,123,'REVIEW')")
            sqlite.execSQL("INSERT INTO flashcard_collections (id,user_uid,title) VALUES (1,'alice','My cards')")
            sqlite.execSQL("INSERT INTO quiz_attempts (id,user_uid,is_placement,total_questions,correct_count,completed_at) VALUES (1,'alice',1,3,2,123)")
            sqlite.version = 2
        }
        val db = Room.databaseBuilder(context, EngMateDatabase::class.java, name).addMigrations(MergeDatabaseMigration).build()
        try {
            assertEquals(7, db.flashcards().getProgress("alice", 1)!!.intervalDays)
            assertEquals("My cards", db.flashcards().getCollection("alice", 1)!!.title)
            assertEquals(2, db.quiz().observeAttempts("alice").first().single().correctCount)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun mainVersionTwoPreservesGrammarQuizAndAnswers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-main-v2.db"
        context.deleteDatabase(name)
        val schema = javaClass.classLoader!!.getResourceAsStream("main-v2.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) sqlite.execSQL(queries.getString(i))
            sqlite.execSQL("INSERT INTO user_profile VALUES ('alice','a@example.com','An',NULL,'A1',15,1,1)")
            sqlite.execSQL("INSERT INTO grammar_lessons (id,title,level,content,examples) VALUES (1,'Present simple','A1','Rules','[{\"en\":\"I work.\",\"vi\":\"Tôi làm việc.\"}]')")
            sqlite.execSQL("INSERT INTO questions (id,lesson_id,answer_type,content) VALUES (1,1,'FILL_BLANK','I ___ every day.')")
            sqlite.execSQL("INSERT INTO question_options (id,question_id,content,is_correct) VALUES (1,1,'work',1)")
            sqlite.execSQL("INSERT INTO quiz_attempts (id,user_id,lesson_id,correct_count,total_questions,completed_at) VALUES (1,'alice',1,1,1,123)")
            sqlite.execSQL("INSERT INTO attempt_answers (id,attempt_id,question_id,answer_text,is_correct) VALUES (1,1,1,'work',1)")
            sqlite.version = 2
        }
        val db = Room.databaseBuilder(context, EngMateDatabase::class.java, name)
            .addMigrations(MergeDatabaseMigration).build()
        try {
            assertEquals("An", db.profiles().observe("alice").first()!!.displayName)
            assertEquals("Tôi làm việc.", db.grammarLessons().get(1)!!.examples.single().vi)
            assertEquals(AnswerType.TEXT, db.questions().get(1)!!.question.answerType)
            assertEquals("work", db.questions().get(1)!!.options.single().content)
            val attempt = db.quiz().getAttempt("alice", 1)!!
            assertEquals(1, attempt.attempt.correctCount)
            assertEquals("I ___ every day.", attempt.answers.single().questionSnapshot)
            db.vocabulary().insertTopic(TopicEntity(title = "New topic"))
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

}
