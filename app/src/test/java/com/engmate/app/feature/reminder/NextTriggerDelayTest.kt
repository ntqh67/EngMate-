package com.engmate.app.feature.reminder

import com.engmate.app.feature.reminder.domain.ReminderSettings
import com.engmate.app.feature.reminder.domain.formatTime
import com.engmate.app.feature.reminder.domain.nextTriggerDelay
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NextTriggerDelayTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(text: String, zone: ZoneId = vn) = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test fun laterTodayFiresToday() =
        assertEquals(Duration.ofHours(2), nextTriggerDelay(at("2026-11-18T18:00:00"), 20, 0, vn))

    @Test fun exactlyNowMovesToTomorrow() =
        assertEquals(Duration.ofDays(1), nextTriggerDelay(at("2026-11-18T20:00:00"), 20, 0, vn))

    @Test fun withinTheCurrentMinuteMovesToTomorrow() =
        assertEquals(Duration.ofHours(23).plusMinutes(59).plusSeconds(30), nextTriggerDelay(at("2026-11-18T20:00:30"), 20, 0, vn))

    @Test fun pastTimeMovesToTomorrow() =
        assertEquals(Duration.ofHours(20), nextTriggerDelay(at("2026-11-18T21:00:00"), 17, 0, vn))

    @Test fun crossesMidnight() {
        assertEquals(Duration.ofMinutes(45), nextTriggerDelay(at("2026-11-18T23:30:00"), 0, 15, vn))
        assertEquals(Duration.ofHours(23).plusMinutes(59), nextTriggerDelay(at("2026-11-19T00:00:00"), 23, 59, vn))
        // New year: date rolls over the month and year.
        assertEquals(Duration.ofMinutes(10), nextTriggerDelay(at("2026-12-31T23:55:00"), 0, 5, vn))
    }

    @Test fun daylightSavingDayIsShorter() {
        val berlin = ZoneId.of("Europe/Berlin") // clocks go forward on 2026-03-29
        assertEquals(Duration.ofHours(23), nextTriggerDelay(at("2026-03-28T20:00:00", berlin), 20, 0, berlin))
    }

    @Test fun rejectsInvalidTimes() {
        assertThrows(IllegalArgumentException::class.java) { nextTriggerDelay(at("2026-11-18T18:00:00"), 24, 0, vn) }
        assertThrows(IllegalArgumentException::class.java) { nextTriggerDelay(at("2026-11-18T18:00:00"), 20, 60, vn) }
        assertThrows(IllegalArgumentException::class.java) { ReminderSettings(hour = -1) }
    }

    @Test fun formatsTimeWithTwoDigits() {
        assertEquals("07:05", formatTime(7, 5))
        assertEquals("20:00", ReminderSettings().timeText)
    }
}
