package app.awn.quran.feature.recite

import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HifzRulesTest {

    @Test
    fun outsideHifzTheTrackerStateIsShownAsIs() {
        WordState.entries.forEach {
            assertEquals(it, HifzRules.displayState(it, hifzEnabled = false))
        }
    }

    @Test
    fun inHifzOnlyConfirmedAndWrongWordsAreRevealed() {
        assertEquals(WordState.Confirmed, HifzRules.displayState(WordState.Confirmed, true))
        assertEquals(WordState.Wrong, HifzRules.displayState(WordState.Wrong, true))
        assertEquals(WordState.Hidden, HifzRules.displayState(WordState.Tentative, true))
        assertEquals(WordState.Hidden, HifzRules.displayState(WordState.Neutral, true))
    }

    @Test
    fun firstLetterSkipsMarksAndReturnsTheBaseLetter() {
        assertEquals("ب", HifzRules.firstLetter("بِسْمِ"))
        assertEquals("ٱ", HifzRules.firstLetter("ٱللَّهِ"))
    }

    @Test
    fun firstLetterOfTextWithoutLettersIsNull() {
        assertNull(HifzRules.firstLetter("ۖ"))
    }
}
