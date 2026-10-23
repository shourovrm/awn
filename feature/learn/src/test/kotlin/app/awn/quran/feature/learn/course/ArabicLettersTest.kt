package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicLettersTest {

    @Test
    fun eachLetterKeepsTheVowelMarksThatFollowIt() {
        // إِنَّ is alif-hamza with a kasra, then noon with shaddah and fatha.
        assertEquals(listOf(0..1, 2..4), ArabicLetters.ranges("إِنَّ"))
    }

    @Test
    fun theWaslaAlifCountsAsALetter() {
        val ranges = ArabicLetters.ranges("ٱلنَّاسِ")

        assertEquals(5, ranges.size)
        assertEquals("نَّ", "ٱلنَّاسِ".substring(ranges[2]))
    }

    @Test
    fun aPauseSignAfterTheWordIsNotPartOfIt() {
        assertEquals("رَّبِّهِمْ", ArabicLetters.withoutPauseSign("رَّبِّهِمْ ۖ"))
    }

    @Test
    fun positionsPastTheEndAreIgnored() {
        assertEquals(listOf(2..4), ArabicLetters.rangesAt("إِنَّ", listOf(2, 7)))
    }
}
