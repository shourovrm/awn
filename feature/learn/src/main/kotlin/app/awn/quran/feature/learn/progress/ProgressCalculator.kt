package app.awn.quran.feature.learn.progress

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.data.AyahEventRow
import app.awn.quran.feature.learn.data.CardEntity
import app.awn.quran.feature.learn.data.PageOpenRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The numbers shown on the Learn and Progress screens, computed from raw events and cards. Every
 * function takes the time zone as an argument, because the day boundary is the device's midnight.
 */
object ProgressCalculator {

    /** The days in a week of the "pages this week" figure: today and the six days before it. */
    const val DAYS_IN_WEEK = 7

    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * Counts the consecutive days with at least one event, ending today. A day without events yet
     * today does not break the streak: it still counts up to yesterday, so a user who has not
     * opened the app this morning still sees yesterday's streak.
     */
    fun currentStreak(eventTimes: List<Long>, today: LocalDate, zone: ZoneId): Int {
        val activeDays = eventTimes.map { localDate(it, zone) }.toSet()
        var day = if (today in activeDays) today else today.minusDays(1)
        var streak = 0
        while (day in activeDays) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }

    /** Distinct pages opened on each day; opening the same page twice in a day counts once. */
    fun pagesPerDay(opens: List<PageOpenRow>, zone: ZoneId): Map<LocalDate, Int> =
        opens
            .groupBy { localDate(it.at, zone) }
            .mapValues { (_, dayOpens) -> dayOpens.map { it.page }.toSet().size }

    /** Pages read on [today] and the six days before it, summed over days. */
    fun pagesThisWeek(opens: List<PageOpenRow>, today: LocalDate, zone: ZoneId): Int {
        val firstDay = today.minusDays((DAYS_IN_WEEK - 1).toLong())
        return pagesPerDay(opens, zone)
            .filterKeys { day -> day >= firstDay && day <= today }
            .values
            .sum()
    }

    /**
     * Ayahs with at least [threshold] skip-free hifz completions on [threshold] different days.
     * Several completions on one day count once, so the ayah cannot be memorised in one sitting.
     */
    fun memorisedAyahs(
        cleanCompletions: List<AyahEventRow>,
        threshold: Int,
        zone: ZoneId,
    ): Set<AyahRef> =
        cleanCompletions
            .groupBy { AyahRef(it.surah, it.ayah) }
            .filterValues { completions ->
                completions.map { localDate(it.at, zone) }.toSet().size >= threshold
            }
            .keys

    fun memorisedBySurah(memorised: Set<AyahRef>): Map<Int, Int> =
        memorised.groupingBy { it.surah }.eachCount().toSortedMap()

    /** Cards due by the end of [today], overdue ones first. */
    fun dueToday(cards: List<CardEntity>, today: LocalDate, zone: ZoneId): List<CardEntity> =
        cards
            .filter { localDate(it.due, zone) <= today }
            .sortedWith(compareBy({ it.due }, { it.surah }, { it.ayah }))

    /** The earliest due time of any card, or null when there are no cards. */
    fun nextDue(cards: List<CardEntity>): Instant? =
        cards.minOfOrNull { it.due }?.let { Instant.ofEpochMilli(it) }
}
