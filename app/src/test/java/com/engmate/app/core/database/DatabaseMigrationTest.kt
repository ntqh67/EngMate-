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
        val db = Room.databaseBuilder(context, EngMateDatabase::class.java, name).build()
        try {
            val profile = db.profiles().observe("legacy").first()!!
            assertEquals("Nguyễn An", profile.displayName)
            assertEquals(30, profile.dailyGoalMinutes)
            assertEquals(100L, profile.createdAt)
            assertEquals(200L, profile.updatedAt)
            db.vocabulary().insertTopic(TopicEntity(title = "Migrated"))
            assertEquals(1, db.vocabulary().observeTopics().first().size)
            db.openHelper.readableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
