package com.engmate.app.di

import com.engmate.app.feature.profile.domain.ProfileRepository
import com.engmate.app.sync.SyncedProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Live: profile is saved in Room and mirrored to Cloud Firestore. */
@Module @InstallIn(SingletonComponent::class)
abstract class ProfileSyncModule {
    @Binds @Singleton abstract fun profile(implementation: SyncedProfileRepository): ProfileRepository
}
