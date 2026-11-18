package com.engmate.app.di

import com.engmate.app.core.contracts.EmptyTopicLookup
import com.engmate.app.core.contracts.ProgressRecorder
import com.engmate.app.core.contracts.TopicLookup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Contracts between features. See docs/integration-dong.md. */
@Module @InstallIn(SingletonComponent::class)
object IntegrationContractsModule {
    @Provides @Singleton fun progressRecorder(study: com.engmate.app.feature.progress.data.local.StudyDao): ProgressRecorder =
        com.engmate.app.feature.progress.data.RoomProgressRecorder(study)

    // Khang: when the real implementation exists, replace only this line; do not change the callers.
    @Provides @Singleton fun topicLookup(): TopicLookup = EmptyTopicLookup()
}
