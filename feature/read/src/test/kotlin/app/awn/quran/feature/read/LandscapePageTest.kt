package app.awn.quran.feature.read

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageGeometry
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.page.pageHeightFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A landscape screen (2392 x 1080 px at 420 dpi, less the page header and number) must show the
 * page as wide as the screen with the proportions of a printed page, and scroll vertically.
 */
class LandscapePageTest {
    private val widthPx = 2392f
    private val bodyHeightPx = 900f
    private val wordsPerLine = 8
    private val wordWidthEm = 1.5f

    private fun fullPageRows() =
        (1..PageGeometry.SLOTS).map { slot ->
            PageRow.TextRow(
                slot,
                List(wordsPerLine) { index ->
                    LineToken.WordToken(WordKey(2, slot, index + 1), "w$slot-$index")
                },
            )
        }

    private fun place(scale: Float = 1f): Pair<Float, PageGeometry> {
        val heightPx = pageHeightFor(widthPx, bodyHeightPx, scale)
        val geometry =
            layoutPage(
                rows = fullPageRows(),
                isCentredBlock = false,
                widthPx = widthPx,
                heightPx = heightPx,
                tokenWidthEm = { wordWidthEm },
                scale = scale,
            )
        return heightPx to geometry
    }

    @Test
    fun theLandscapePageKeepsThePrintedProportionsAndScrolls() {
        val (heightPx, _) = place()

        assertEquals(widthPx * 1.5f, heightPx, 0.5f)
        assertTrue("taller than the body, so it scrolls", heightPx > bodyHeightPx)
    }

    @Test
    fun theLinesSpanTheScreenWidth() {
        val (_, geometry) = place()

        val frameWidth = geometry.frameRight - geometry.frameLeft
        assertTrue("frame $frameWidth of $widthPx", frameWidth >= widthPx * 0.95f)
    }

    @Test
    fun noLineOverlapsTheNextOne() {
        val (_, geometry) = place()

        assertTrue(
            "font ${geometry.fontSizePx} in slot ${geometry.slotHeightPx}",
            geometry.fontSizePx <= geometry.slotHeightPx,
        )
        val tops = geometry.textLines.map { it.top }
        tops.zipWithNext().forEach { (upper, lower) ->
            assertTrue(lower - upper >= geometry.fontSizePx)
        }
    }

    @Test
    fun portraitPagesAreAsTallAsTheBody() {
        assertEquals(1800f, pageHeightFor(900f, 1800f, 1f), 0.01f)
        assertEquals(2070f, pageHeightFor(900f, 1800f, 1.15f), 0.01f)
    }
}
