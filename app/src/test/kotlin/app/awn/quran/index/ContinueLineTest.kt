package app.awn.quran.index

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueLineTest {

    // Every character is 10 wide, so a string of n characters is 10 n.
    private val measure: (String) -> Float = { it.length * 10f }
    private val words = listOf("alpha", "beta", "gamma", "delta")

    @Test
    fun aLineThatFitsIsShownWhole() {
        assertEquals("alpha beta gamma delta", fitWholeWords(words, 1000f, measure))
    }

    @Test
    fun aLongLineEndsOnAWholeWordFollowedByAnEllipsis() {
        // "alpha beta …" is 12 characters, "alpha beta gamma …" is 18.
        assertEquals("alpha beta …", fitWholeWords(words, 150f, measure))
    }

    @Test
    fun aWordIsNeverCutEvenWhenTheFirstOneDoesNotFit() {
        assertEquals("alpha …", fitWholeWords(words, 20f, measure))
    }

    @Test
    fun noWordsGiveAnEmptyLine() {
        assertEquals("", fitWholeWords(emptyList(), 100f, measure))
    }
}
