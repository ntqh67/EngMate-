package com.engmate.app.di

import com.engmate.app.feature.reminder.domain.ReminderScheduler
import com.engmate.app.feature.reminder.worker.WorkManagerReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds @Singleton abstract fun scheduler(implementation: WorkManagerReminderScheduler): ReminderScheduler
}
