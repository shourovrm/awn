package app.awn.quran.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JuzRailTest {

    private val rows =
        listOf<IndexRow>(
            JuzHeading(juz = 1, startPage = 1, continuedChapter = null),
            JuzHeading(juz = 2, startPage = 22, continuedChapter = null),
        )

    @Test
    fun theTopOfTheRailIsJuzOneAndTheBottomIsJuzThirty() {
        assertEquals(1, JuzRail.juzAtFraction(0f))
        assertEquals(30, JuzRail.juzAtFraction(1f))
    }

    @Test
    fun aFingerPastTheEndsStaysOnTheFirstAndLastJuz() {
        assertEquals(1, JuzRail.juzAtFraction(-0.4f))
        assertEquals(30, JuzRail.juzAtFraction(3f))
    }

    @Test
    fun everyJuzIsFoundAtItsOwnPositionOnTheRail() {
        for (juz in 1..30) {
            assertEquals(juz, JuzRail.juzAtFraction(JuzRail.fractionOfJuz(juz)))
        }
    }

    @Test
    fun theCurrentJuzIsThatOfTheFirstVisibleRow() {
        assertEquals(1, JuzRail.currentJuz(rows, firstVisibleRow = 0))
        assertEquals(2, JuzRail.currentJuz(rows, firstVisibleRow = 1))
    }

    @Test
    fun aListScrolledAboveItsFirstRowIsOnJuzOneAndPastItsEndOnTheLast() {
        assertEquals(1, JuzRail.currentJuz(rows, firstVisibleRow = -3))
        assertEquals(2, JuzRail.currentJuz(rows, firstVisibleRow = 9))
        assertNull(JuzRail.currentJuz(emptyList(), firstVisibleRow = 0))
    }

    @Test
    fun theRailPrintsSevenLandmarks() {
        assertEquals(listOf(1, 5, 10, 15, 20, 25, 30), JuzRail.landmarks)
    }
}
