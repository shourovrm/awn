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

class ReviseStateTest {

    private val zone = ZoneId.of("UTC")
    // A Monday, so the strip starts on Tuesday as in the design.
    private val today = LocalDate.of(2026, 10, 26)

    private fun snapshot(
        dueToday: List<AyahRef> = emptyList(),
        dueNextSevenDays: List<Int> = List(7) { 0 },
        nextDue: Instant? = null,
        nextDueAyahs: List<AyahRef> = emptyList(),
    ) =
        ProgressSnapshot(
            streakDays = 0,
            pagesToday = 0,
            pagesThisWeek = 0,
            memorisedByAyah = emptySet(),
            memorisedBySurah = emptyMap(),
            memorisationThreshold = 3,
            dueToday = dueToday,
            nextDue = nextDue,
            dueNextSevenDays = dueNextSevenDays,
            nextDueAyahs = nextDueAyahs,
        )

    private fun state(snapshot: ProgressSnapshot) =
        LearnUiMapper.reviseState(snapshot, { "Surah $it" }, today, zone, Locale.ENGLISH)

    @Test
    fun dueAyahsAreGroupedBySurahWithTheirSpan() {
        val due =
            listOf(AyahRef(112, 3), AyahRef(1, 2), AyahRef(1, 5), AyahRef(1, 3), AyahRef(1, 4))

        val state = state(snapshot(dueToday = due))

        assertTrue(state.isSomethingDue)
        assertEquals(5, state.dueCount)
        assertEquals(
            listOf(DueSurah("Surah 1", 2, 5, 4), DueSurah("Surah 112", 3, 3, 1)),
            state.dueSurahs,
        )
    }

    @Test
    fun theStripStartsTomorrowWithShortWeekdayNames() {
        val state = state(snapshot(dueNextSevenDays = listOf(3, 0, 5, 2, 0, 6, 1)))

        assertEquals(
            listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon"),
            state.upcomingDays.map { it.label },
        )
        assertEquals(listOf(3, 0, 5, 2, 0, 6, 1), state.upcomingDays.map { it.dueCount })
        assertTrue(state.hasUpcoming)
    }

    @Test
    fun nothingDueNamesTheWeekdayOfTheNextRevision() {
        val nextDue = Instant.parse("2026-10-27T05:00:00Z")

        val state =
            state(
                snapshot(
                    nextDue = nextDue,
                    nextDueAyahs = listOf(AyahRef(113, 1), AyahRef(113, 2), AyahRef(113, 3)),
                ),
            )

        assertFalse(state.isSomethingDue)
        assertEquals("Tuesday", state.nextRevisionDay)
        assertEquals(3, state.nextRevisionCount)
    }

    @Test
    fun aRevisionMoreThanAWeekAwayIsNamedByDate() {
        val nextDue = Instant.parse("2026-11-20T05:00:00Z")

        val state = state(snapshot(nextDue = nextDue))

        assertEquals("Nov 20, 2026", state.nextRevisionDay)
    }

    @Test
    fun withoutAnyCardThereIsNoNextRevisionAndNoStrip() {
        val state = state(snapshot())

        assertNull(state.nextRevisionDay)
        assertEquals(0, state.nextRevisionCount)
        assertFalse(state.hasUpcoming)
    }
}
