package app.awn.quran.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A result is cut to three lines, so a match late in a long text was hidden behind the ellipsis.
 * [windowBeforeFirstMatch] picks the part to show.
 */
class ResultWindowTest {

    private val text =
        "And those who disbelieve and deny Our verses are the companions of the Fire and mercy"

    private fun rangeOf(word: String, within: String = text): IntRange {
        val start = within.indexOf(word)
        return start until start + word.length
    }

    @Test
    fun aMatchNearTheStartLeavesTheTextAsItIs() {
        val matches = listOf(rangeOf("disbelieve"))

        assertEquals(ResultWindow(text, matches), windowBeforeFirstMatch(text, matches))
    }

    @Test
    fun noMatchLeavesTheTextAsItIs() {
        assertEquals(ResultWindow(text, emptyList()), windowBeforeFirstMatch(text, emptyList()))
    }

    @Test
    fun aLateMatchStartsThreeWordsBeforeItBehindAnEllipsis() {
        val window = windowBeforeFirstMatch(text, listOf(rangeOf("mercy")))

        assertEquals("…the Fire and mercy", window.text)
    }

    @Test
    fun theMovedRangeStillCoversTheMatchedWord() {
        val window = windowBeforeFirstMatch(text, listOf(rangeOf("mercy")))

        assertEquals("mercy", window.text.substring(window.matches.single()))
    }

    @Test
    fun everyRangeMovesByTheSameDistance() {
        val matches = listOf(rangeOf("mercy"), rangeOf("Fire"))
        val sorted = matches.sortedBy { it.first }
        val window = windowBeforeFirstMatch(text, sorted)

        assertEquals(listOf("Fire", "mercy"), window.matches.map { window.text.substring(it) })
    }

    @Test
    fun aMatchInsideAWordStartsTheWindowCountedFromThatWord() {
        val insideTheWord = text.indexOf("ercy")..text.length - 1
        val window = windowBeforeFirstMatch(text, listOf(insideTheWord), wordsBefore = 1)

        assertEquals("…and mercy", window.text)
        assertEquals("ercy", window.text.substring(window.matches.single()))
    }

    @Test
    fun fewerWordsBeforeTheMatchThanWantedKeepsTheWholeText() {
        val short = "the mercy"
        val matches = listOf(rangeOf("mercy", short))

        assertEquals(ResultWindow(short, matches), windowBeforeFirstMatch(short, matches))
    }

    @Test
    fun anArabicWindowKeepsEveryLetterWithItsMarks() {
        val arabic =
            "وَٱلَّذِينَ كَفَرُوا۟ وَكَذَّبُوا۟ بِـَٔايَٰتِنَآ أُو۟لَٰٓئِكَ أَصْحَٰبُ ٱلنَّارِ هُمْ فِيهَا خَٰلِدُونَ"
        val start = arabic.indexOf("خَٰلِدُونَ")
        val matches = listOf(start until arabic.length)

        val window = windowBeforeFirstMatch(arabic, matches)

        assertEquals("خَٰلِدُونَ", window.text.substring(window.matches.single()))
        assertEquals("…ٱلنَّارِ هُمْ فِيهَا خَٰلِدُونَ", window.text)
    }
}
