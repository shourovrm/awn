package app.awn.quran.feature.read

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.awn.quran.feature.read.page.SelectionBounds
import app.awn.quran.feature.read.quran.placeSelectionBar
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionBarPlacementTest {
    private val screen = IntSize(1000, 2000)
    private val bar = IntSize(300, 100)
    private val gap = 20
    private val flipLimit = 150f

    private fun boundsOf(firstLineTop: Float, lastLineBottom: Float, left: Float, right: Float) =
        SelectionBounds(
            firstLine = Rect(left, firstLineTop, right, firstLineTop + 100f),
            all = Rect(left, firstLineTop, right, lastLineBottom),
        )

    @Test
    fun theBarSitsAboveTheFirstLineCentredOnTheSelection() {
        val bounds = boundsOf(500f, 700f, 300f, 700f)

        val place = placeSelectionBar(bounds, bar, screen, gap, flipLimit)

        // Centre 500 minus half the bar width 150; first line top 500 minus bar 100 minus gap 20.
        assertEquals(IntOffset(350, 380), place)
    }

    @Test
    fun theBarFlipsBelowWhenTheFirstLineIsWithinTheLimitOfTheTop() {
        val bounds = boundsOf(140f, 400f, 300f, 700f)

        val place = placeSelectionBar(bounds, bar, screen, gap, flipLimit)

        assertEquals(400 + gap, place.y)
    }

    @Test
    fun theBarStaysAboveWhenTheFirstLineIsExactlyAtTheLimit() {
        val bounds = boundsOf(150f, 400f, 300f, 700f)

        val place = placeSelectionBar(bounds, bar, screen, gap, flipLimit)

        assertEquals(150 - 100 - gap, place.y)
    }

    @Test
    fun theBarGoesUnderTheFirstLineWhenBelowTheSelectionWouldLeaveTheScreen() {
        // A long selection from near the top to the bottom edge: above and below both fail.
        val bounds = boundsOf(100f, 1990f, 300f, 700f)

        val place = placeSelectionBar(bounds, bar, screen, gap, flipLimit)

        assertEquals(100 + 100 + gap, place.y)
    }

    @Test
    fun theBarKeepsTheGapFromTheScreenEdges() {
        val leftEdge =
            placeSelectionBar(boundsOf(500f, 700f, 0f, 100f), bar, screen, gap, flipLimit)
        val rightEdge =
            placeSelectionBar(boundsOf(500f, 700f, 900f, 1000f), bar, screen, gap, flipLimit)

        assertEquals(gap, leftEdge.x)
        assertEquals(screen.width - bar.width - gap, rightEdge.x)
    }

    @Test
    fun aBarWiderThanTheScreenStartsAtTheLeftEdge() {
        val wideBar = IntSize(screen.width, 100)

        val place =
            placeSelectionBar(boundsOf(500f, 700f, 0f, 100f), wideBar, screen, gap, flipLimit)

        assertEquals(0, place.x)
    }
}
