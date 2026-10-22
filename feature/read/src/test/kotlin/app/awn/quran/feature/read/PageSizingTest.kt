package app.awn.quran.feature.read

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageGeometry
import app.awn.quran.feature.read.page.PagePlacement
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.page.pageHeightFor
import app.awn.quran.feature.read.page.planPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The size of the printed page: smaller keeps the lines, bigger pours the words into new lines. */
class PageSizingTest {
    private val portraitWidth = 1000f
    private val portraitBody = 1800f
    private val landscapeWidth = 2392f
    private val landscapeBody = 900f
    private val wordsPerLine = 8

    /** Every word is 1.5 ems wide. */
    private val emWidth: (LineToken) -> Float = { 1.5f }

    private fun fullPageRows(): List<PageRow> =
        (1..PageGeometry.SLOTS).map { slot ->
            PageRow.TextRow(
                slot,
                List(wordsPerLine) { index ->
                    LineToken.WordToken(WordKey(2, slot, index + 1), "w$slot-$index")
                },
            )
        }

    private fun plan(
        sizeFactor: Float,
        width: Float = portraitWidth,
        body: Float = portraitBody,
    ) = planPage(fullPageRows(), PagePlacement(width, body, sizeFactor, false), emWidth)

    @Test
    fun theDefaultSizeIsThePrintedPageAsBefore() {
        val plan = plan(1f)
        val printed = layoutPage(fullPageRows(), false, portraitWidth, portraitBody, emWidth)

        assertEquals(PageGeometry.SLOTS, plan.rows.size)
        assertEquals(printed.fontSizePx, plan.geometry.fontSizePx, 0.01f)
        assertEquals(printed.slotHeightPx, plan.geometry.slotHeightPx, 0.01f)
        assertEquals(portraitBody, plan.pageHeightPx, 0.01f)
        assertEquals(printed.frameLeft, plan.geometry.frameLeft, 0.01f)
    }

    @Test
    fun theLandscapeDefaultIsThePrintedProportionsAndScrolls() {
        val plan = plan(1f, landscapeWidth, landscapeBody)

        assertEquals(pageHeightFor(landscapeWidth, landscapeBody), plan.pageHeightPx, 0.01f)
        assertTrue(plan.pageHeightPx > landscapeBody)
    }

    @Test
    fun aSmallerSizeKeepsThePrintedLinesInANarrowerCentredColumn() {
        val default = plan(1f)
        val smaller = plan(0.7f)

        assertEquals(PageGeometry.SLOTS, smaller.rows.size)
        assertEquals(default.geometry.fontSizePx * 0.7f, smaller.geometry.fontSizePx, 0.01f)
        val columnWidth = smaller.geometry.frameRight - smaller.geometry.frameLeft
        val defaultColumnWidth = default.geometry.frameRight - default.geometry.frameLeft
        assertEquals(defaultColumnWidth * 0.7f, columnWidth, 0.5f)
        assertEquals(portraitWidth / 2f, smaller.geometry.frameLeft + columnWidth / 2f, 0.5f)
    }

    @Test
    fun aShorterPageSitsInTheMiddleOfTheBody() {
        val smaller = plan(0.7f)

        assertEquals(portraitBody * 0.7f, smaller.pageHeightPx, 0.5f)
        val expectedTop = (portraitBody - smaller.pageHeightPx) / 2f
        assertEquals(expectedTop, smaller.geometry.slotTop(1), 0.5f)
    }

    @Test
    fun aSmallerSizeInLandscapeKeepsTheLinesAndStillScrolls() {
        val default = plan(1f, landscapeWidth, landscapeBody)
        val smaller = plan(0.7f, landscapeWidth, landscapeBody)

        assertEquals(PageGeometry.SLOTS, smaller.rows.size)
        assertEquals(default.geometry.fontSizePx * 0.7f, smaller.geometry.fontSizePx, 0.01f)
        assertTrue(smaller.pageHeightPx > landscapeBody)
    }

    @Test
    fun aBiggerSizeThatStillFitsKeepsThePrintedLines() {
        // Eight words of 1.5 em are 12 em; with the narrowest gaps the line has room to grow 5 %.
        val bigger = plan(1.05f)

        assertEquals(PageGeometry.SLOTS, bigger.rows.size)
    }

    @Test
    fun aSizeThatMakesALineTooWideGivesNewLinesThatFitTheWidth() {
        val bigger = plan(2f)

        assertTrue(bigger.rows.size > PageGeometry.SLOTS)
        assertTrue(bigger.pageHeightPx > portraitBody)
        for (line in bigger.geometry.textLines) {
            assertTrue(line.tokens.first().right <= portraitWidth + 0.5f)
            assertTrue(line.tokens.last().left >= -0.5f)
        }
    }

    @Test
    fun theFontAndTheSlotGrowTogetherWhenLinesAreBroken() {
        val default = plan(1f)
        val bigger = plan(2f)

        assertEquals(
            default.geometry.slotHeightPx / default.geometry.fontSizePx,
            bigger.geometry.slotHeightPx / bigger.geometry.fontSizePx,
            0.001f,
        )
        assertEquals(default.geometry.fontSizePx * 2f, bigger.geometry.fontSizePx, 0.01f)
    }

    @Test
    fun everyWordIsStillOnThePageAfterBreaking() {
        val bigger = plan(2f)

        val before = fullPageRows().flatMap { row -> (row as PageRow.TextRow).tokens }
        val after = bigger.geometry.textLines.flatMap { line -> line.tokens.map { it.token } }
        assertEquals(before, after)
    }

    @Test
    fun theFontNeverGrowsPastTheWidthOfTheWidestWord() {
        val plan = plan(50f)

        // A word is 1.5 em wide, so no font may be larger than the width over 1.5.
        assertTrue(plan.geometry.fontSizePx <= portraitWidth / 1.5f + 0.01f)
    }

    @Test
    fun theCentredBlockOfPageOneStaysInsideItsFrameWhenEnlarged() {
        fun textRow(slot: Int, wordCount: Int) =
            PageRow.TextRow(
                slot,
                List(wordCount) { index ->
                    LineToken.WordToken(WordKey(1, slot, index + 1), "c$slot-$index")
                },
            )
        val rows = listOf(textRow(3, 4), textRow(4, 5), textRow(8, 3))

        val plan = planPage(rows, PagePlacement(portraitWidth, portraitBody, 2f, true), emWidth)

        for (line in plan.geometry.textLines) {
            assertTrue(line.tokens.first().right <= plan.geometry.frameRight + 0.01f)
            assertTrue(line.tokens.last().left >= plan.geometry.frameLeft - 0.01f)
        }
        assertTrue(
            plan.geometry.frameRight - plan.geometry.frameLeft <= portraitWidth * 0.9f + 0.01f,
        )
        val lastLine = plan.geometry.textLines.last()
        assertTrue(lastLine.top + plan.geometry.slotHeightPx <= plan.pageHeightPx + 0.5f)
    }
}
