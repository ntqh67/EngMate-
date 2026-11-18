package com.engmate.app.feature.reminder.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.engmate.app.feature.reminder.domain.ReminderScheduler
import com.engmate.app.feature.reminder.domain.nextTriggerDelay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * One unique one-time work per reminder; the worker schedules the next day itself. WorkManager keeps
 * pending work across reboots. Delivery is not minute-exact: battery saving can delay it a few minutes.
 */
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {
    override fun schedule(hour: Int, minute: Int) {
        val delay = nextTriggerDelay(clock.instant(), hour, minute, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            // The worker gets the time with the request, so it never reads DataStore outside Hilt.
            .setInputData(workDataOf(ReminderWorker.KEY_HOUR to hour, ReminderWorker.KEY_MINUTE to minute))
            .addTag(WORK_NAME)
            .build()
        // REPLACE drops any work still holding an old time.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "daily_study_reminder"
    }
}
