package app.awn.quran.feature.learn.progress

import app.awn.quran.feature.learn.data.CardEntity
import app.awn.quran.feature.learn.data.PageOpenRow
import java.time.LocalDate
import java.time.ZoneId

/**
 * The seven-day figures of the Progress week chart. They are split from [ProgressCalculator] so
 * that object stays small; both use the same device-midnight day boundary.
 */
object WeekProgress {

    /** The seven days of the week view, oldest first, ending today. */
    fun lastSevenDays(today: LocalDate): List<LocalDate> =
        (ProgressCalculator.DAYS_IN_WEEK - 1 downTo 0).map { daysBack ->
            today.minusDays(daysBack.toLong())
        }

    /** The days among [lastSevenDays] on which the user did anything in the app. */
    fun activeDaysThisWeek(eventTimes: List<Long>, today: LocalDate, zone: ZoneId): Set<LocalDate> {
        val week = lastSevenDays(today).toSet()
        return eventTimes
            .map { ProgressCalculator.localDate(it, zone) }
            .filter { it in week }
            .toSet()
    }

    /** Distinct pages read on each of [lastSevenDays], in the same order. */
    fun pagesEachDayThisWeek(opens: List<PageOpenRow>, today: LocalDate, zone: ZoneId): List<Int> {
        val pagesByDay = ProgressCalculator.pagesPerDay(opens, zone)
        return lastSevenDays(today).map { day -> pagesByDay[day] ?: 0 }
    }

    /**
     * How many cards fall due on each of the seven days after [today], tomorrow first. Cards that
     * are due today or overdue are in [dueToday], not here.
     */
    fun dueCountsNextSevenDays(cards: List<CardEntity>, today: LocalDate, zone: ZoneId): List<Int> {
        val dueDays = cards.map { ProgressCalculator.localDate(it.due, zone) }
        return (1..ProgressCalculator.DAYS_IN_WEEK).map { daysAhead ->
            val day = today.plusDays(daysAhead.toLong())
            dueDays.count { it == day }
        }
    }
}
