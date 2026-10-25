package app.awn.quran.search

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.search.SearchHit
import app.awn.quran.core.data.search.SearchSource
import app.awn.quran.core.ui.text.normalizeFallbackText
import app.awn.quran.core.ui.text.startsWithCombiningMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicResultTextTest {

    private val ayah = AyahRef(2, 2)

    // U+06DB is a pause mark; the stored words put a space before it.
    private val pauseMark = "ۛ"
    private val words = listOf("ذَٰلِكَ", "ٱلْكِتَـٰبُ", "لَا", "رَيْبَ $pauseMark", "فِيهِ")

    private fun markedText(hit: SearchHit): List<String> =
        hit.matches.map { range -> hit.text.substring(range) }

    @Test
    fun aPauseMarkJoinsItsWordInsteadOfStartingOnASpace() {
        val hit = recitedHit(ayah, words, firstWord = 0, lastWord = 4)

        assertFalse(hit.text.contains(" $pauseMark"))
        assertTrue(hit.text.split(" ").none { startsWithCombiningMark(it) })
    }

    @Test
    fun theRecitedWordsAreOneMarkedRange() {
        val hit = recitedHit(ayah, words, firstWord = 1, lastWord = 3)

        assertEquals(
            listOf(normalizeFallbackText("ٱلْكِتَـٰبُ لَا رَيْبَ$pauseMark")),
            markedText(hit),
        )
    }

    @Test
    fun aSingleWordIsMarkedAlone() {
        val hit = recitedHit(ayah, words, firstWord = 4, lastWord = 4)

        assertEquals(listOf("فِيهِ"), markedText(hit))
    }

    @Test
    fun aWordNumberPastTheTextEndsAtTheLastWord() {
        val hit = recitedHit(ayah, words, firstWord = 3, lastWord = 9)

        assertEquals(listOf(normalizeFallbackText("رَيْبَ$pauseMark فِيهِ")), markedText(hit))
    }

    @Test
    fun aFirstWordPastTheTextMarksNothing() {
        assertEquals(
            emptyList<IntRange>(),
            recitedHit(ayah, words, firstWord = 7, lastWord = 8).matches,
        )
    }

    @Test
    fun aTypedHitKeepsItsMarkedWordsWhenSpacesBeforePauseMarksGo() {
        val text = words.joinToString(" ")
        val start = text.indexOf("فِيهِ")
        val hit = SearchHit(ayah, SearchSource.Arabic, text, listOf(start until text.length))

        val shown = hit.withFallbackText()

        assertFalse(shown.text.contains(" $pauseMark"))
        assertEquals(listOf("فِيهِ"), markedText(shown))
    }

    @Test
    fun aTranslationHitIsLeftAsItIs() {
        val hit =
            SearchHit(
                ayah,
                SearchSource.Translation(TranslationId("en")),
                "No doubt ",
                listOf(0..1),
            )

        assertEquals(hit, hit.withFallbackText())
    }
}
