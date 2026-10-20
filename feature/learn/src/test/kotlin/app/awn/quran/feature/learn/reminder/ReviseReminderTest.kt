package app.awn.quran.feature.learn.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviseReminderTest {

    private val zone = ZoneId.of("Asia/Dhaka")

    private fun at(text: String): ZonedDateTime =
        ZonedDateTime.parse(text).withZoneSameInstant(zone)

    @Test
    fun theNextTriggerIsTodayWhenTheTimeIsStillAhead() {
        val now = at("2026-03-10T08:00:00+06:00")

        val next = ReviseReminder.nextTrigger(now, hour = 20, minute = 30)

        assertEquals(at("2026-03-10T20:30:00+06:00"), next)
    }

    @Test
    fun theNextTriggerIsTomorrowWhenTheTimeHasPassed() {
        val now = at("2026-03-10T21:00:00+06:00")

        val next = ReviseReminder.nextTrigger(now, hour = 20, minute = 30)

        assertEquals(at("2026-03-11T20:30:00+06:00"), next)
    }

    @Test
    fun theNextTriggerIsTomorrowAtTheExactMinute() {
        val now = at("2026-03-10T20:30:00+06:00")

        val next = ReviseReminder.nextTrigger(now, hour = 20, minute = 30)

        assertEquals(at("2026-03-11T20:30:00+06:00"), next)
    }

    @Test
    fun theNextTriggerKeepsTheWallClockTimeAcrossASummerTimeChange() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.parse("2026-03-07T22:00:00-05:00").withZoneSameInstant(newYork)

        val next = ReviseReminder.nextTrigger(now, hour = 8, minute = 0)

        assertEquals(8, next.hour)
        assertEquals(8, next.dayOfMonth)
    }

    @Test
    fun noNotificationIsShownWhenNothingIsDue() {
        assertFalse(ReviseReminder.shouldNotify(dueCount = 0))
        assertTrue(ReviseReminder.shouldNotify(dueCount = 1))
    }
}
