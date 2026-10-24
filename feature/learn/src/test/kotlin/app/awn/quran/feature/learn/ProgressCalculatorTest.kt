package app.awn.quran.feature.learn

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.data.AyahEventRow
import app.awn.quran.feature.learn.data.CardEntity
import app.awn.quran.feature.learn.data.PageOpenRow
import app.awn.quran.feature.learn.progress.ProgressCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressCalculatorTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 3, 10)

    private fun millis(
        date: LocalDate,
        hour: Int,
        minute: Int,
        second: Int = 0,
        zone: ZoneId = dhaka,
    ) =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute, second)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    // Streak

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val times = listOf(0L, 1L, 2L).map { millis(today.minusDays(it), 9, 0) }

        assertEquals(3, ProgressCalculator.currentStreak(times, today, dhaka))
    }

    @Test
    fun streakSurvivesUntilTheFirstEventOfToday() {
        val times = listOf(1L, 2L).map { millis(today.minusDays(it), 9, 0) }

        assertEquals(2, ProgressCalculator.currentStreak(times, today, dhaka))
    }

    @Test
    fun streakIsBrokenByAMissedDay() {
        val times = listOf(millis(today, 9, 0), millis(today.minusDays(2), 9, 0))

        assertEquals(1, ProgressCalculator.currentStreak(times, today, dhaka))
    }

    @Test
    fun streakIsZeroAfterTwoQuietDays() {
        val times = listOf(millis(today.minusDays(2), 9, 0))

        assertEquals(0, ProgressCalculator.currentStreak(times, today, dhaka))
    }

    @Test
    fun streakIsZeroWithoutEvents() {
        assertEquals(0, ProgressCalculator.currentStreak(emptyList(), today, dhaka))
    }

    @Test
    fun oneSecondBeforeMidnightAndMidnightAreDifferentDays() {
        val lastSecondOfYesterday = millis(today.minusDays(1), 23, 59, 59)
        val firstSecondOfToday = millis(today, 0, 0, 0)

        assertEquals(
            1,
            ProgressCalculator.currentStreak(
                listOf(lastSecondOfYesterday),
                today.minusDays(1),
                dhaka,
            ),
        )
        assertEquals(
            2,
            ProgressCalculator.currentStreak(
                listOf(lastSecondOfYesterday, firstSecondOfToday),
                today,
                dhaka,
            ),
        )
        assertEquals(1, ProgressCalculator.currentStreak(listOf(firstSecondOfToday), today, dhaka))
    }

    @Test
    fun theDayBoundaryFollowsTheDeviceTimeZone() {
        // 20:00 UTC is 02:00 the next day in Dhaka (UTC+6).
        val instant = millis(today, 20, 0, zone = utc)

        assertEquals(today, ProgressCalculator.localDate(instant, utc))
        assertEquals(today.plusDays(1), ProgressCalculator.localDate(instant, dhaka))
    }

    // Pages

    @Test
    fun reopeningAPageOnTheSameDayCountsOnce() {
        val opens =
            listOf(
                PageOpenRow(page = 5, at = millis(today, 8, 0)),
                PageOpenRow(page = 5, at = millis(today, 20, 0)),
                PageOpenRow(page = 6, at = millis(today, 21, 0)),
            )

        assertEquals(mapOf(today to 2), ProgressCalculator.pagesPerDay(opens, dhaka))
    }

    @Test
    fun pagesThisWeekCoversTodayAndTheSixDaysBefore() {
        val opens =
            listOf(
                PageOpenRow(1, millis(today, 8, 0)),
                PageOpenRow(2, millis(today.minusDays(6), 8, 0)),
                PageOpenRow(3, millis(today.minusDays(7), 8, 0)),
                PageOpenRow(4, millis(today.plusDays(1), 8, 0)),
            )

        assertEquals(2, ProgressCalculator.pagesThisWeek(opens, today, dhaka))
    }

    // Memorisation

    private fun cleanCompletion(surah: Int, ayah: Int, date: LocalDate, hour: Int = 9) =
        AyahEventRow(surah, ayah, millis(date, hour, 0))

    @Test
    fun threeCleanCompletionsOnThreeDaysMemoriseAnAyah() {
        val completions = (0L..2L).map { cleanCompletion(1, 2, today.minusDays(it)) }

        val memorised = ProgressCalculator.memorisedAyahs(completions, threshold = 3, zone = dhaka)

        assertEquals(setOf(AyahRef(1, 2)), memorised)
    }

    @Test
    fun threeCompletionsOnOneDayDoNotMemoriseAnAyah() {
        val completions = listOf(9, 12, 15).map { cleanCompletion(1, 2, today, hour = it) }

        assertEquals(emptySet<AyahRef>(), ProgressCalculator.memorisedAyahs(completions, 3, dhaka))
    }

    @Test
    fun twoDaysAreNotEnoughForThreeButEnoughForTwo() {
        val completions =
            listOf(cleanCompletion(1, 2, today), cleanCompletion(1, 2, today.minusDays(1)))

        assertEquals(emptySet<AyahRef>(), ProgressCalculator.memorisedAyahs(completions, 3, dhaka))
        assertEquals(setOf(AyahRef(1, 2)), ProgressCalculator.memorisedAyahs(completions, 2, dhaka))
    }

    @Test
    fun midnightSplitsTwoCompletionsIntoTwoDays() {
        val completions =
            listOf(
                AyahEventRow(2, 255, millis(today.minusDays(1), 23, 59, 59)),
                AyahEventRow(2, 255, millis(today, 0, 0, 1)),
            )

        assertEquals(
            setOf(AyahRef(2, 255)),
            ProgressCalculator.memorisedAyahs(completions, 2, dhaka),
        )
    }

    @Test
    fun memorisedAyahsAreGroupedBySurahInSurahOrder() {
        val memorised = setOf(AyahRef(2, 1), AyahRef(1, 1), AyahRef(1, 2))

        assertEquals(mapOf(1 to 2, 2 to 1), ProgressCalculator.memorisedBySurah(memorised))
        assertEquals(listOf(1, 2), ProgressCalculator.memorisedBySurah(memorised).keys.toList())
    }

    // Due cards

    private fun card(ayah: Int, due: Long) =
        CardEntity(
            surah = 1,
            ayah = ayah,
            stability = 2.0,
            difficulty = 5.0,
            due = due,
            lastReview = 0,
            reps = 1,
            lapses = 0,
            state = 2,
        )

    @Test
    fun dueTodayIncludesOverdueAndLaterTodayButNotTomorrow() {
        val cards =
            listOf(
                card(1, millis(today.minusDays(3), 9, 0)),
                card(2, millis(today, 23, 59, 59)),
                card(3, millis(today.plusDays(1), 0, 0, 0)),
                card(4, millis(today, 6, 0)),
            )

        val due = ProgressCalculator.dueToday(cards, today, dhaka)

        assertEquals(listOf(1, 4, 2), due.map { it.ayah })
    }

    @Test
    fun nextDueIsTheEarliestDueTime() {
        val cards = listOf(card(1, 5_000), card(2, 1_000), card(3, 9_000))

        assertEquals(1_000L, ProgressCalculator.nextDue(cards)?.toEpochMilli())
        assertNull(ProgressCalculator.nextDue(emptyList()))
    }

    // The week view

    @Test
    fun lastSevenDaysEndTodayAndStartSixDaysBefore() {
        val week = ProgressCalculator.lastSevenDays(today)

        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first())
        assertEquals(today, week.last())
    }

    @Test
    fun activeDaysThisWeekIgnoresDaysBeforeTheWeek() {
        val times =
            listOf(
                millis(today, 9, 0),
                millis(today, 20, 0),
                millis(today.minusDays(6), 9, 0),
                millis(today.minusDays(7), 9, 0),
            )

        assertEquals(
            setOf(today, today.minusDays(6)),
            ProgressCalculator.activeDaysThisWeek(times, today, dhaka),
        )
    }

    @Test
    fun pagesEachDayThisWeekCountsDistinctPagesAndFillsQuietDaysWithZero() {
        val opens =
            listOf(
                PageOpenRow(page = 5, at = millis(today, 9, 0)),
                PageOpenRow(page = 5, at = millis(today, 10, 0)),
                PageOpenRow(page = 6, at = millis(today, 11, 0)),
                PageOpenRow(page = 9, at = millis(today.minusDays(2), 9, 0)),
                PageOpenRow(page = 9, at = millis(today.minusDays(8), 9, 0)),
            )

        assertEquals(
            listOf(0, 0, 0, 0, 1, 0, 2),
            ProgressCalculator.pagesEachDayThisWeek(opens, today, dhaka),
        )
    }

    @Test
    fun dueCountsNextSevenDaysStartTomorrowAndSkipTodayAndLaterThanAWeek() {
        val cards =
            listOf(
                card(1, millis(today, 9, 0)),
                card(2, millis(today.plusDays(1), 9, 0)),
                card(3, millis(today.plusDays(1), 22, 0)),
                card(4, millis(today.plusDays(3), 9, 0)),
                card(5, millis(today.plusDays(7), 9, 0)),
                card(6, millis(today.plusDays(8), 9, 0)),
            )

        assertEquals(
            listOf(2, 0, 1, 0, 0, 0, 1),
            ProgressCalculator.dueCountsNextSevenDays(cards, today, dhaka),
        )
    }

    @Test
    fun ayahsDueOnNextDayAreThoseOfTheEarliestDueDayInReadingOrder() {
        val cards =
            listOf(
                card(7, millis(today.plusDays(2), 20, 0)),
                card(3, millis(today.plusDays(2), 8, 0)),
                card(1, millis(today.plusDays(4), 8, 0)),
            )

        assertEquals(
            listOf(AyahRef(1, 3), AyahRef(1, 7)),
            ProgressCalculator.ayahsDueOnNextDay(cards, dhaka),
        )
        assertEquals(emptyList<AyahRef>(), ProgressCalculator.ayahsDueOnNextDay(emptyList(), dhaka))
    }
}
