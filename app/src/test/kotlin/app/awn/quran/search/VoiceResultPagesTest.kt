package app.awn.quran.search

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceResultPagesTest {

    private val fortyRows = (1..40).toList()

    @Test
    fun theFirstPageShowsFifteenRows() {
        assertEquals((1..15).toList(), visibleVoiceRows(fortyRows, pageCount = 1))
    }

    @Test
    fun eachPageAddsFifteenRows() {
        assertEquals((1..30).toList(), visibleVoiceRows(fortyRows, pageCount = 2))
    }

    @Test
    fun aLastPageShowsOnlyTheRowsLeft() {
        assertEquals(fortyRows, visibleVoiceRows(fortyRows, pageCount = 3))
    }

    @Test
    fun aShortListIsShownWhole() {
        assertEquals(listOf(1, 2, 3), visibleVoiceRows(listOf(1, 2, 3), pageCount = 1))
    }
}
