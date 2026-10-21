package app.awn.quran.feature.read.quran

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.awn.quran.feature.read.page.SelectionBounds

/**
 * Where the top left corner of the floating bar goes, in the coordinates of the screen.
 *
 * The bar sits [gapPx] above the first line of the selection. When the first line is closer than
 * [flipBelowTopPx] to the top of the screen there is no room above, so the bar flips below the last
 * line. When below does not fit either, the bar sits under the first line, over the selection.
 * Horizontally the bar is centred on the selection and kept [gapPx] inside the screen edges, so a
 * word card for the first word of a line does not touch the edge of the glass.
 */
internal fun placeSelectionBar(
    bounds: SelectionBounds,
    barSize: IntSize,
    screenSize: IntSize,
    gapPx: Int,
    flipBelowTopPx: Float,
): IntOffset {
    val centredLeft = bounds.all.center.x - barSize.width / 2f
    val rightmost = screenSize.width - barSize.width - gapPx
    val x =
        if (rightmost < gapPx) {
            ((screenSize.width - barSize.width) / 2).coerceAtLeast(0)
        } else {
            centredLeft.toInt().coerceIn(gapPx, rightmost)
        }

    val above = bounds.firstLine.top - barSize.height - gapPx
    val below = bounds.all.bottom + gapPx
    val y =
        when {
            bounds.firstLine.top >= flipBelowTopPx && above >= 0f -> above
            below + barSize.height <= screenSize.height -> below
            else -> bounds.firstLine.bottom + gapPx
        }
    return IntOffset(x, y.toInt())
}
