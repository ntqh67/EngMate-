package com.engmate.app.feature.reminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.engmate.app.core.datastore.PreferenceKeys
import com.engmate.app.feature.reminder.domain.ReminderSettings
import com.engmate.app.feature.reminder.domain.requireValidTime
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Reminder settings in the shared DataStore. Device-wide on purpose (see docs/integration-dong.md). */
class ReminderRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<ReminderSettings> = dataStore.data
        // A corrupt or unreadable file falls back to the defaults instead of crashing the screen.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val hour = prefs[HOUR]?.takeIf { it in 0..23 } ?: ReminderSettings.DEFAULT_HOUR
            val minute = prefs[MINUTE]?.takeIf { it in 0..59 } ?: ReminderSettings.DEFAULT_MINUTE
            ReminderSettings(prefs[ENABLED] ?: false, hour, minute)
        }

    suspend fun setEnabled(enabled: Boolean) { dataStore.edit { it[ENABLED] = enabled } }

    /** Throws IllegalArgumentException for a time outside 00:00–23:59. */
    suspend fun setTime(hour: Int, minute: Int) {
        requireValidTime(hour, minute)
        dataStore.edit { it[HOUR] = hour; it[MINUTE] = minute }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey(PreferenceKeys.REMINDER_PREFIX + "enabled")
        val HOUR = intPreferencesKey(PreferenceKeys.REMINDER_PREFIX + "hour")
        val MINUTE = intPreferencesKey(PreferenceKeys.REMINDER_PREFIX + "minute")
    }
}
