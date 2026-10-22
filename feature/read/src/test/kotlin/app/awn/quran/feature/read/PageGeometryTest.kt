package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.page.selectionBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGeometryTest {

    private val width = 1000f
    private val height = 1500f

    private fun word(surah: Int, ayah: Int, position: Int) =
        LineToken.WordToken(WordKey(surah, ayah, position), "w$surah-$ayah-$position")

    /** Every word is 2 ems wide, every end sign 1 em. */
    private val emWidth: (LineToken) -> Float = { token ->
        when (token) {
            is LineToken.WordToken -> 2f
            is LineToken.RosetteToken -> 1f
        }
    }

    private fun fullLine(slot: Int, ayah: Int, wordCount: Int): PageRow.TextRow =
        PageRow.TextRow(slot, List(wordCount) { word(2, ayah, it + 1) })

    @Test
    fun linesThatFillThePrintAreJustifiedToTheSameEdges() {
        val rows =
            listOf(
                fullLine(1, 1, 6),
                // 5 words and a sign: 11 ems against 12 ems for the widest line, so more than 80 %.
                PageRow.TextRow(
                    2,
                    List(5) { word(2, 2, it + 1) } + LineToken.RosetteToken(AyahRef(2, 2), null),
                ),
            )

        val geometry =
            layoutPage(rows, isCentredBlock = false, widthPx = width, heightPx = height, emWidth)

        for (line in geometry.textLines) {
            assertEquals(geometry.frameRight, line.tokens.first().right, 0.01f)
            assertEquals(geometry.frameLeft, line.tokens.last().left, 0.01f)
        }
        assertEquals(0f, geometry.frameLeft, 0.01f)
        assertEquals(width, geometry.frameRight, 0.01f)
    }

    @Test
    fun aShortLastLineStaysShortAndCentred() {
        val rows = listOf(fullLine(1, 1, 6), fullLine(2, 2, 2))

        val geometry = layoutPage(rows, false, width, height, emWidth)

        val shortLine = geometry.textLines[1]
        val left = shortLine.tokens.last().left
        val right = shortLine.tokens.first().right
        assertTrue(right - left < width * 0.6f)
        assertEquals(width / 2f, (left + right) / 2f, 0.5f)
    }

    @Test
    fun normalPagesUseFifteenEqualSlotsOverTheWholeHeight() {
        val geometry =
            layoutPage(listOf(fullLine(1, 1, 6), fullLine(15, 2, 6)), false, width, height, emWidth)

        assertEquals(height / 15f, geometry.slotHeightPx, 0.01f)
        assertEquals(0f, geometry.slotTop(1), 0.01f)
        assertEquals(height - height / 15f, geometry.slotTop(15), 0.01f)
    }

    @Test
    fun pagesOneAndTwoAreACentredBlockWithTheBannerAtTwentySevenPercent() {
        val rows = listOf(fullLine(3, 1, 4), fullLine(4, 2, 5), fullLine(8, 3, 3))

        val geometry =
            layoutPage(rows, isCentredBlock = true, widthPx = width, heightPx = height, emWidth)

        assertEquals(height * 0.27f, geometry.slotCenter(1), 0.01f)
        assertTrue(geometry.slotTop(8) + geometry.slotHeightPx < height)
        for (line in geometry.textLines) {
            val center = (line.tokens.first().right + line.tokens.last().left) / 2f
            assertEquals(width / 2f, center, 0.5f)
        }
        // Not stretched to the 15-line frame: all eight slots fit inside it.
        assertTrue(geometry.slotHeightPx * 8 < height * 0.9f)
        assertTrue(geometry.frameRight - geometry.frameLeft <= width * 0.9f + 0.01f)
    }

    @Test
    fun aMultiLineAyahHasOneFragmentPerLineAndABoundsRectangleOfTheUnion() {
        val rows =
            listOf(
                PageRow.TextRow(4, List(6) { word(2, 7, it + 1) }),
                PageRow.TextRow(5, List(6) { word(2, 7, it + 7) }),
                PageRow.TextRow(6, List(2) { word(2, 8, it + 1) } + List(2) { word(2, 9, it + 1) }),
            )
        val geometry = layoutPage(rows, false, width, height, emWidth)

        val fragments = geometry.ayahFragments(AyahRef(2, 7))

        assertEquals(2, fragments.size)
        assertEquals(geometry.slotTop(4), fragments[0].top, 0.01f)
        assertEquals(geometry.slotTop(5), fragments[1].top, 0.01f)
        val bounds = geometry.ayahBounds(AyahRef(2, 7))!!
        assertEquals(fragments.minOf { it.left }, bounds.left, 0.01f)
        assertEquals(geometry.slotTop(4), bounds.top, 0.01f)
        assertEquals(geometry.slotTop(5) + geometry.slotHeightPx, bounds.bottom, 0.01f)
    }

    @Test
    fun aFragmentCoversOnlyTheWordsOfItsAyahOnASharedLine() {
        val rows =
            listOf(
                PageRow.TextRow(1, List(6) { word(2, 6, it + 1) }),
                PageRow.TextRow(2, List(3) { word(2, 7, it + 1) } + List(3) { word(2, 8, it + 1) }),
            )
        val geometry = layoutPage(rows, false, width, height, emWidth)

        val first = geometry.ayahFragments(AyahRef(2, 7)).single()
        val second = geometry.ayahFragments(AyahRef(2, 8)).single()

        // Reading is right to left, so ayah 7 sits at the right of ayah 8 and they do not overlap.
        assertTrue(first.left >= second.right)
        assertEquals(geometry.frameRight, first.right, 0.01f)
        assertEquals(geometry.frameLeft, second.left, 0.01f)
    }

    @Test
    fun hitTestFindsTheWordAndReturnsNothingOutsideTheLines() {
        val rows = listOf(fullLine(1, 1, 3))
        val geometry = layoutPage(rows, false, width, height, emWidth)
        val firstWord = geometry.textLines.single().tokens.first()

        val token =
            geometry.tokenAt((firstWord.left + firstWord.right) / 2f, geometry.slotCenter(1))

        assertEquals(firstWord.token, token)
        assertNull(geometry.tokenAt(width / 2f, geometry.slotCenter(5)))
        assertNotNull(geometry.wordRect(WordKey(2, 1, 2)))
        assertNull(geometry.wordRect(WordKey(2, 1, 9)))
        assertNull(geometry.ayahBounds(AyahRef(3, 1)))
    }

    // Three lines of two ayahs each: slot 1 holds ayah 1 and the start of ayah 2 (words 1-3 and
    // 4-6), slot 2 all of ayah 3, slot 3 ayah 4. Every word is 2 ems, justified to the page.
    private fun hitRows() =
        listOf(
            PageRow.TextRow(1, List(3) { word(2, 1, it + 1) } + List(3) { word(2, 2, it + 1) }),
            PageRow.TextRow(2, List(6) { word(2, 3, it + 1) }),
            PageRow.TextRow(3, List(6) { word(2, 4, it + 1) }),
        )

    private fun hitGeometry() = layoutPage(hitRows(), false, width, height, emWidth)

    @Test
    fun aTouchOnAWordFindsItsAyah() {
        val geometry = hitGeometry()
        val word = geometry.textLines[0].tokens.first() // first word of ayah 1, at the right edge

        val ayah = geometry.nearestAyahAt(word.right - 1f, geometry.slotTop(1) + 5f)

        assertEquals(AyahRef(2, 1), ayah)
    }

    @Test
    fun aTouchBetweenLinesFindsTheNearestLine() {
        val geometry = hitGeometry()
        // Below the last line of text, in the bottom margin of the page.
        assertEquals(AyahRef(2, 4), geometry.nearestAyahAt(500f, height - 5f))
        // Above the first line.
        assertEquals(AyahRef(2, 1), geometry.nearestAyahAt(900f, -40f))
    }

    @Test
    fun aTouchInTheSideMarginFindsTheNearestAyahOnThatLine() {
        val geometry = hitGeometry()
        val middleOfFirstLine = geometry.slotTop(1) + geometry.slotHeightPx / 2f

        // Far right of the page: the first token on the line belongs to ayah 1.
        assertEquals(AyahRef(2, 1), geometry.nearestAyahAt(width + 300f, middleOfFirstLine))
        // Far left: the last token on the line belongs to ayah 2.
        assertEquals(AyahRef(2, 2), geometry.nearestAyahAt(-300f, middleOfFirstLine))
    }

    @Test
    fun aPageWithoutTextHasNoNearestAyah() {
        val empty = layoutPage(emptyList(), false, width, height, emWidth)

        assertNull(empty.nearestAyahAt(10f, 10f))
    }

    @Test
    fun selectionBoundsSplitTheFirstLineFromTheWholeSelection() {
        val geometry = hitGeometry()

        val bounds = geometry.selectionBounds(setOf(AyahRef(2, 2), AyahRef(2, 3)))

        assertNotNull(bounds)
        // Ayah 2 starts on slot 1 and ayah 3 fills slot 2.
        assertEquals(geometry.slotTop(1), bounds!!.firstLine.top, 0.01f)
        assertEquals(geometry.slotTop(1) + geometry.slotHeightPx, bounds.firstLine.bottom, 0.01f)
        assertEquals(geometry.slotTop(2) + geometry.slotHeightPx, bounds.all.bottom, 0.01f)
        assertNull(geometry.selectionBounds(setOf(AyahRef(9, 9))))
    }
}
