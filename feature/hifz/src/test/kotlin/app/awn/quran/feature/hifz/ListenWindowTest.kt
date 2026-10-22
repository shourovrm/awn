package app.awn.quran.feature.hifz

import app.awn.quran.feature.hifz.method.ListenWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Hifz bar's Play always looped the lesson's own ayahs, so the user could not listen on to the
 * next ayahs or back to the ones before. Next and Previous on the bar move the listening window by
 * its own length through the print, across surahs, and stop at the ends of the Quran.
 */
class ListenWindowTest {

    private val table = smallTable()

    @Test
    fun nextMovesToTheSameNumberOfAyahsAfterTheWindow() {
        assertEquals(
            ListenWindow(ayah(9), ayah(12)),
            ListenWindow(ayah(5), ayah(8)).next(table),
        )
    }

    @Test
    fun previousMovesToTheSameNumberOfAyahsBeforeTheWindow() {
        assertEquals(
            ListenWindow(ayah(1), ayah(4)),
            ListenWindow(ayah(5), ayah(8)).previous(table),
        )
    }

    @Test
    fun previousNearTheStartTakesWhatIsLeft() {
        assertEquals(
            ListenWindow(ayah(1), ayah(2)),
            ListenWindow(ayah(3), ayah(6)).previous(table),
        )
    }

    @Test
    fun nextNearTheEndTakesWhatIsLeft() {
        assertEquals(
            ListenWindow(ayah(47), ayah(48)),
            ListenWindow(ayah(43), ayah(46)).next(table),
        )
    }

    @Test
    fun thereIsNothingBeforeTheFirstAyah() {
        assertNull(ListenWindow(ayah(1), ayah(4)).previous(table))
    }

    @Test
    fun thereIsNothingAfterTheLastAyah() {
        assertNull(ListenWindow(ayah(45), ayah(48)).next(table))
    }

    @Test
    fun aOneAyahWindowStepsOneAyah() {
        assertEquals(ListenWindow(ayah(13), ayah(13)), ListenWindow(ayah(12), ayah(12)).next(table))
    }
}
