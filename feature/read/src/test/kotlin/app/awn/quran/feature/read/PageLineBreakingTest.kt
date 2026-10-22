package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.breakRowsToWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLineBreakingTest {

    /** A word is 2 ems wide, an end sign 1 em; the gap between tokens is 0.18 em. */
    private val emWidth: (LineToken) -> Float = { token ->
        when (token) {
            is LineToken.WordToken -> 2f
            is LineToken.RosetteToken -> 1f
        }
    }

    private fun word(ayah: Int, position: Int) =
        LineToken.WordToken(WordKey(2, ayah, position), "w$ayah-$position")

    private fun textRow(slot: Int, ayah: Int, wordCount: Int) =
        PageRow.TextRow(slot, List(wordCount) { word(ayah, it + 1) })

    private fun wordsOf(rows: List<PageRow>): List<WordKey> =
        rows
            .filterIsInstance<PageRow.TextRow>()
            .flatMap { it.tokens }
            .filterIsInstance<LineToken.WordToken>()
            .map { it.key }

    @Test
    fun linesThatFitAreKeptAsPrinted() {
        val rows = listOf(textRow(1, 1, 5), textRow(2, 2, 4))
        // 5 words with 4 squeezed gaps: 10.16 em.
        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 10.2f)

        assertSame(rows, result)
    }

    @Test
    fun aLineThatFitsOnlyWithSqueezedGapsIsStillKept() {
        val rows = listOf(textRow(1, 1, 5))

        // 10 em of words, and the natural gaps would make it 10.72 em.
        assertSame(rows, breakRowsToWidth(rows, emWidth, maxLineWidthEm = 10.3f))
    }

    @Test
    fun aTooWideLineIsPouredIntoLinesThatFit() {
        val rows = listOf(textRow(1, 1, 5), textRow(2, 2, 5))

        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 7f)

        // Three words take 6.36 em with natural gaps, four take 8.54 em.
        val lengths = result.filterIsInstance<PageRow.TextRow>().map { it.tokens.size }
        assertEquals(listOf(3, 3, 3, 1), lengths)
        assertEquals(listOf(1, 2, 3, 4), result.map { it.slot })
    }

    @Test
    fun wordsKeepTheirReadingOrderAndNoWordIsLostOrSplit() {
        val rows = listOf(textRow(1, 1, 5), textRow(2, 2, 5), textRow(3, 3, 5))

        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 5f)

        assertEquals(wordsOf(rows), wordsOf(result))
    }

    @Test
    fun anEndSignStaysWithTheLastWordOfItsAyah() {
        val sign = LineToken.RosetteToken(AyahRef(2, 1), glyphCode = null)
        // The third word and its sign are 3.18 em together, so they move to the next line as one.
        val rows = listOf(PageRow.TextRow(1, listOf(word(1, 1), word(1, 2), word(1, 3), sign)))

        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 5.5f)

        val lines = result.filterIsInstance<PageRow.TextRow>().map { it.tokens }
        assertEquals(listOf(word(1, 1), word(1, 2)), lines[0])
        assertEquals(listOf(word(1, 3), sign), lines[1])
    }

    @Test
    fun aBannerAndABasmalahKeepTheirRowsBetweenTheRuns() {
        val rows =
            listOf(
                textRow(1, 1, 5),
                PageRow.HeaderRow(2, surah = 3),
                PageRow.BasmalaRow(3),
                textRow(4, 1, 5),
            )

        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 7f)

        val kinds = result.map { it::class.simpleName }
        assertEquals(
            listOf("TextRow", "TextRow", "HeaderRow", "BasmalaRow", "TextRow", "TextRow"),
            kinds,
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), result.map { it.slot })
        assertTrue(result[2] is PageRow.HeaderRow && (result[2] as PageRow.HeaderRow).surah == 3)
    }

    @Test
    fun aWordWiderThanTheLineStandsAloneOnItsOwnLine() {
        val rows = listOf(textRow(1, 1, 3))

        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 1.5f)

        assertEquals(
            listOf(1, 1, 1),
            result.filterIsInstance<PageRow.TextRow>().map { it.tokens.size },
        )
    }

    @Test
    fun numberingStartsAtTheSlotOfTheFirstRow() {
        val rows = listOf(textRow(3, 1, 5))

        val result = breakRowsToWidth(rows, emWidth, maxLineWidthEm = 5f)

        assertEquals(3, result.first().slot)
    }
}
