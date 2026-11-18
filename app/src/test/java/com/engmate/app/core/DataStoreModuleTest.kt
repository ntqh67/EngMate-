package com.engmate.app.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.engmate.app.di.DataStoreModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class DataStoreModuleTest {
    @Test fun settingsStoreReadsBackWhatWasWritten() = runBlocking<Unit> {
        val store = DataStoreModule.settings(ApplicationProvider.getApplicationContext<Context>())
        val key = stringPreferencesKey("theme_mode")
        store.edit { it[key] = "dark" }
        assertEquals("dark", store.data.first()[key])
    }
}
