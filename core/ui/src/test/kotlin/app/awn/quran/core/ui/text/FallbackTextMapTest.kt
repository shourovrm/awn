package app.awn.quran.core.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackTextMapTest {

    // U+06DB is a pause mark; the Quran.com text puts a space before it.
    private val pauseMark = "ۛ"

    @Test
    fun theTextIsTheSameAsNormalizeFallbackText() {
        val text = "رَيْبَ $pauseMark فِيهِ"

        assertEquals(normalizeFallbackText(text), normalizeFallbackTextWithMap(text).text)
    }

    @Test
    fun aRangeAfterARemovedSpaceMovesLeftByOne() {
        val text = "رَيْبَ $pauseMark فِيهِ"
        val start = text.indexOf("فِيهِ")

        val mapped = normalizeFallbackTextWithMap(text).mapRange(start until text.length)

        assertEquals("فِيهِ", normalizeFallbackText(text).substring(mapped.first, mapped.last + 1))
    }

    @Test
    fun aRangeBeforeTheRemovedSpaceStaysWhereItIs() {
        val text = "رَيْبَ $pauseMark فِيهِ"
        val word = 0 until "رَيْبَ".length

        val mapped = normalizeFallbackTextWithMap(text).mapRange(word)

        assertEquals(word, mapped)
    }

    @Test
    fun directionMarksAndTrailingSpacesAreDropped() {
        val text = "\u200Fبِسْمِ "
        val mapped = normalizeFallbackTextWithMap(text)

        val letters = "بِسْمِ".length
        assertEquals(letters, mapped.text.length)
        assertEquals(0 until letters, mapped.mapRange(1 until text.length))
    }
}
