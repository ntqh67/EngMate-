package com.engmate.app.feature.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.engmate.app.feature.reminder.data.ReminderRepository
import com.engmate.app.feature.reminder.domain.ReminderSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

/** Shared helper: a real preferences DataStore in a temporary file. */
internal class TempDataStore(folder: TemporaryFolder) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file: File = File(folder.newFolder(), "engmate_settings.preferences_pb")
    val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
    fun close() = scope.cancel()
}

class ReminderRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var temp: TempDataStore
    private lateinit var repository: ReminderRepository

    @Before fun setup() { temp = TempDataStore(folder); repository = ReminderRepository(temp.store) }
    @After fun tearDown() = temp.close()

    @Test fun defaultsToOffAtEightPm() = runBlocking {
        assertEquals(ReminderSettings(enabled = false, hour = 20, minute = 0), repository.settings.first())
    }

    @Test fun savesSwitchAndTime() = runBlocking {
        repository.setEnabled(true)
        repository.setTime(7, 30)
        assertEquals(ReminderSettings(enabled = true, hour = 7, minute = 30), repository.settings.first())
        repository.setEnabled(false)
        assertEquals(ReminderSettings(enabled = false, hour = 7, minute = 30), repository.settings.first())
    }

    @Test fun invalidTimeIsRejectedAndNotStored() = runBlocking {
        repository.setTime(6, 15)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setTime(24, 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setTime(8, -1) } }
        assertEquals(6, repository.settings.first().hour)
        assertEquals(15, repository.settings.first().minute)
    }
}
