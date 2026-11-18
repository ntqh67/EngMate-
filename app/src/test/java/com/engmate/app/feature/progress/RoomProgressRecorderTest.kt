package com.engmate.app.feature.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.progress.data.RoomProgressRecorder
import com.engmate.app.feature.progress.presentation.calculateProgress
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class RoomProgressRecorderTest {
    private lateinit var db: EngMateDatabase
    private lateinit var recorder: RoomProgressRecorder
    private val now = 1_760_000_000_000L
    private val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EngMateDatabase::class.java).allowMainThreadQueries().build()
        db.profiles().insert(UserProfileEntity("u", "u@x.com", "An", null, "A2", 15, 1, 1))
        recorder = RoomProgressRecorder(db.study())
    }
    @After fun close() = db.close()

    private suspend fun progress() = calculateProgress(db.study().observeAllStats("u").first(), 0, emptyList(), today)

    @Test fun quizCardAndLessonFeedTheDailyStats() = runBlocking {
        recorder.onQuizCompleted("u", 40, 4, 5, false, now)
        recorder.onCardReviewed("u", "apple", known = true, newlyMastered = true, reviewedAt = now + 1)
        recorder.onCardReviewed("u", "pear", known = false, newlyMastered = false, reviewedAt = now + 2)
        recorder.onLessonStudied("u", 1, now)
        val stat = db.study().getStat("u", today.toString())!!
        assertEquals(40L + 10 + 3 + 5, stat.xpEarned)
        assertEquals(1L, stat.wordsLearned); assertEquals(2L, stat.wordsReviewed); assertEquals(1L, stat.quizzesCompleted)
        assertEquals(1, progress().streak)
    }

    @Test fun lessonIsCountedOncePerDayAndQuizTwiceIsIdempotent() = runBlocking {
        recorder.onLessonStudied("u", 7, now); recorder.onLessonStudied("u", 7, now + 5000)
        recorder.onQuizCompleted("u", 10, 1, 1, false, now); recorder.onQuizCompleted("u", 10, 1, 1, false, now)
        assertEquals(15L, db.study().getStat("u", today.toString())!!.xpEarned)
    }

    @Test fun pronunciationScoreBecomesXp() = runBlocking {
        recorder.onPronunciationPracticed("u", 85, now)
        assertEquals(8L, db.study().getStat("u", today.toString())!!.xpEarned)
    }
}
