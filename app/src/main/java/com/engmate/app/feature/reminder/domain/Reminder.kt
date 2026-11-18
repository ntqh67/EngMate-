package com.engmate.app.feature.reminder.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Device-wide study reminder settings (SRS FR12). Off by default, 20:00. */
data class ReminderSettings(val enabled: Boolean = false, val hour: Int = DEFAULT_HOUR, val minute: Int = DEFAULT_MINUTE) {
    init { requireValidTime(hour, minute) }

    /** "20:05" */
    val timeText: String get() = formatTime(hour, minute)

    companion object {
        const val DEFAULT_HOUR = 20
        const val DEFAULT_MINUTE = 0
    }
}

fun requireValidTime(hour: Int, minute: Int) {
    require(hour in 0..23) { "Giờ phải trong khoảng 0–23" }
    require(minute in 0..59) { "Phút phải trong khoảng 0–59" }
}

fun formatTime(hour: Int, minute: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)

/**
 * Time from [now] until the next [hour]:[minute] in [zone]. A time equal to or before now moves to the
 * next day, so the result is always in (0, 1 day]; a daylight-saving change can make it 23 or 25 hours.
 */
fun nextTriggerDelay(now: Instant, hour: Int, minute: Int, zone: ZoneId): Duration {
    requireValidTime(hour, minute)
    val current = now.atZone(zone)
    var next = ZonedDateTime.of(current.toLocalDate(), LocalTime.of(hour, minute), zone)
    if (!next.toInstant().isAfter(now)) next = ZonedDateTime.of(current.toLocalDate().plusDays(1), LocalTime.of(hour, minute), zone)
    return Duration.between(now, next.toInstant())
}

/** Schedules the daily reminder. The app binds a WorkManager implementation. */
interface ReminderScheduler {
    /** Replaces any pending reminder with one at [hour]:[minute], repeating daily. */
    fun schedule(hour: Int, minute: Int)
    /** Cancels the pending reminder, if any. Safe to call when nothing is scheduled (e.g. on sign-out). */
    fun cancel()
}
