package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.Journal
import app.awn.quran.feature.hifz.method.JournalEntry
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.hifz.method.PageMarking
import app.awn.quran.feature.hifz.method.PageProgressRules
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalAndMarkingTest {

    private val nowMillis = 1_800_000_000_000L
    private val minute = 60_000L

    private fun lesson(daysAgo: Long, memorised: Int, minutes: Long, atMillis: Long = nowMillis) =
        JournalEntry(
            day = testDay.minusDays(daysAgo),
            atMillis = atMillis,
            kind = PartKind.NewLesson,
            pages = listOf(1),
            firstAyah = ayah(1),
            lastAyah = ayah(4),
            grade = PartGrade.Good,
            isComplete = memorised == 4,
            durationMillis = minutes * minute,
            ayahAnswers =
                (1..4).associate {
                    ayah(it) to if (it <= memorised) AyahFirmness.Firm else AyahFirmness.NotYet
                },
            pageGrades = emptyMap(),
        )

    private fun revision(daysAgo: Long, graded: Int) =
        lesson(daysAgo, 0, 10)
            .copy(
                kind = PartKind.Old,
                pages = listOf(1, 2, 3),
                ayahAnswers = emptyMap(),
                pageGrades = (1..graded).associateWith { PartGrade.Good },
            )

    @Test
    fun theWeekCountsMemorisedAyahsRevisedPagesMinutesAndDays() {
        val entries = listOf(lesson(0, 4, 20), lesson(0, 2, 10), revision(3, 2), lesson(8, 4, 30))

        val week = Journal.weekOf(entries, testDay)

        assertEquals(6, week.newAyahs)
        assertEquals(2, week.pagesRevised)
        assertEquals(40 * minute, week.practiceMillis)
        assertEquals(2, week.practiceDays)
    }

    @Test
    fun theStripHasSevenDaysOldestFirstWithZeroForADayOff() {
        val minutes = Journal.minutesByDay(listOf(lesson(0, 4, 20), lesson(2, 4, 15)), testDay)

        assertEquals(7, minutes.size)
        assertEquals(testDay, minutes.last().first)
        assertEquals(listOf(0L, 0L, 0L, 0L, 15L, 0L, 20L), minutes.map { it.second })
    }

    @Test
    fun anEntryCountsTheMistakesFlaggedWhileItWasPractised() {
        val entry = lesson(0, 4, 10)
        val mistakes =
            listOf(
                MistakeRecord(ayah(1), MistakeType.Skipped, nowMillis - 5 * minute),
                MistakeRecord(ayah(2), MistakeType.OtherWord, nowMillis - 20 * minute),
            )

        assertEquals(1, Journal.mistakesDuring(entry, mistakes))
    }

    @Test
    fun markingAKnownPageAgainKeepsItsDatesAndMemory() {
        val revised =
            PageProgressRules.gradeRevision(
                PageMarking.markPage(5, PageStatus.Memorised, testDay.minusDays(40), nowMillis),
                PartGrade.Good,
                testDay.minusDays(10),
                nowMillis,
            )

        val again =
            PageMarking.markPage(5, PageStatus.Memorised, testDay, nowMillis, existing = revised)

        assertEquals(revised, again)
    }

    @Test
    fun markingAKnownPageSolidLiftsAWeakMemoryToThreeWeeks() {
        val memorised = PageMarking.markPage(5, PageStatus.Memorised, testDay, nowMillis)

        val solid =
            PageMarking.markPage(5, PageStatus.Solid, testDay, nowMillis, existing = memorised)

        assertEquals(PageStatus.Solid, solid.status)
        assertEquals(PageProgressRules.SOLID_STABILITY_DAYS, solid.card!!.stability!!, 0.0)
    }

    @Test
    fun pagesMarkedTogetherComeBackSpreadOverAMonth() {
        val days = (0 until 300).map { PageMarking.spreadDays(it, 300) }

        assertEquals(1, days.first())
        assertEquals(30, days.last())
        assertTrue(days.groupingBy { it }.eachCount().values.all { it == 10 })
        assertEquals(listOf(1, 2, 3), (0 until 3).map { PageMarking.spreadDays(it, 3) })
    }

    @Test
    fun aSpreadPageIsFirstDueOnItsOwnDay() {
        val record =
            PageMarking.markPage(
                5,
                PageStatus.Memorised,
                testDay,
                nowMillis,
                firstRevisionInDays = 12,
            )

        assertEquals(nowMillis + 12 * 86_400_000L, record.card!!.dueMillis)
    }
}
