package app.awn.quran.feature.learn

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.progress.ProgressSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressStateTest {

    private val zone = ZoneId.of("UTC")
    // A Monday: the week runs Tuesday to Monday, as in the design.
    private val today = LocalDate.of(2026, 10, 26)

    private val activeReader =
        ProgressSnapshot(
            streakDays = 2,
            pagesToday = 4,
            pagesThisWeek = 13,
            memorisedByAyah = (1..28).map { AyahRef(2, it) }.toSet(),
            memorisedBySurah = mapOf(2 to 28),
            memorisationThreshold = 3,
            dueToday = emptyList(),
            nextDue = Instant.parse("2026-10-27T05:00:00Z"),
            activeDaysThisWeek = setOf(today.minusDays(1), today),
            pagesEachDayThisWeek = listOf(0, 0, 0, 0, 0, 9, 4),
            nextDueAyahs = listOf(AyahRef(113, 1), AyahRef(113, 2), AyahRef(113, 3)),
        )

    private val newUser =
        ProgressSnapshot(
            streakDays = 0,
            pagesToday = 0,
            pagesThisWeek = 0,
            memorisedByAyah = emptySet(),
            memorisedBySurah = emptyMap(),
            memorisationThreshold = 3,
            dueToday = emptyList(),
            nextDue = null,
        )

    private fun state(snapshot: ProgressSnapshot, wordsKnown: Int, wordShare: Double) =
        LearnUiMapper.progressState(snapshot, wordsKnown, wordShare, today, zone, Locale.ENGLISH)

    @Test
    fun theFourFiguresComeFromTheSnapshotAndTheWordCourse() {
        val state = state(activeReader, wordsKnown = 31, wordShare = 0.1234)

        assertEquals(2, state.streakDays)
        assertEquals(13, state.pagesThisWeek)
        assertEquals(28, state.memorisedCount)
        assertEquals(31, state.wordsKnown)
        assertEquals(12, state.quranWordsPercent)
        assertFalse(state.isNewUser)
    }

    @Test
    fun theWeekRunsFromSixDaysAgoToTodayWithActiveDaysAndPages() {
        val state = state(activeReader, wordsKnown = 31, wordShare = 0.12)

        assertEquals(
            listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon"),
            state.days.map { it.label },
        )
        assertEquals(
            listOf(false, false, false, false, false, true, true),
            state.days.map { it.isActive },
        )
        assertEquals(listOf(0, 0, 0, 0, 0, 9, 4), state.days.map { it.pages })
        assertEquals(listOf(6), state.days.withIndex().filter { it.value.isToday }.map { it.index })
    }

    @Test
    fun theNextRevisionIsNamedByWeekdayWithItsAyahCount() {
        val state = state(activeReader, wordsKnown = 31, wordShare = 0.12)

        assertEquals("Tuesday", state.nextRevisionDay)
        assertEquals(3, state.nextRevisionCount)
    }

    @Test
    fun aNewUserHasZeroFiguresSevenQuietDaysAndNoNextRevision() {
        val state = state(newUser, wordsKnown = 0, wordShare = 0.0)

        assertTrue(state.isNewUser)
        assertEquals(0, state.quranWordsPercent)
        assertEquals(7, state.days.size)
        assertTrue(state.days.none { it.isActive || it.pages > 0 })
        assertNull(state.nextRevisionDay)
    }

    @Test
    fun knowingAWordIsEnoughToLeaveTheNewUserState() {
        assertFalse(state(newUser, wordsKnown = 1, wordShare = 0.001).isNewUser)
    }
}
