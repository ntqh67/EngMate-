package com.engmate.app
import android.app.Application
import com.engmate.app.core.sync.ContentSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
@HiltAndroidApp class EngMateApplication : Application() {
    @Inject lateinit var contentSync: ContentSync
    @Inject lateinit var vocabularySeeder: com.engmate.app.feature.vocabulary.data.VocabularySeeder
    override fun onCreate() {
        super.onCreate()
        vocabularySeeder.seedInBackground()
        contentSync.start()
    }
}
