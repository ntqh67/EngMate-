package com.engmate.app.feature.reminder.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.engmate.app.MainActivity
import com.engmate.app.R
import java.time.Clock

/** Shows the daily study notification, then schedules the next one with the same time. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val hour = inputData.getInt(KEY_HOUR, -1)
        val minute = inputData.getInt(KEY_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) return Result.failure()

        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, manager)
        // Without permission the notification is skipped, but the schedule keeps running for when it is granted.
        if (canNotify(context, manager)) manager.notify(NOTIFICATION_ID, build(context))

        // Done last: replacing this unique work may cancel the running worker, and nothing is left to do.
        WorkManagerReminderScheduler(context, Clock.systemUTC()).schedule(hour, minute)
        return Result.success()
    }

    private fun build(context: Context): Notification {
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Đến giờ học rồi!")
            .setContentText("Dành vài phút ôn ngữ pháp và làm một bài tập nhé.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val CHANNEL_ID = "study_reminder"
        private const val NOTIFICATION_ID = 2001

        fun ensureChannel(context: Context, manager: NotificationManager = context.getSystemService(NotificationManager::class.java)) {
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Nhắc học hằng ngày", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Thông báo nhắc bạn học vào giờ đã chọn" })
        }

        fun canNotify(context: Context, manager: NotificationManager = context.getSystemService(NotificationManager::class.java)): Boolean {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return granted && manager.areNotificationsEnabled()
        }
    }
}
