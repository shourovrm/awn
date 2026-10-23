package app.awn.quran.feature.read.page

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnFieldColors

/**
 * The soft fill behind each word of a form the user knows (green) or is learning (ochre, fainter),
 * drawn the way recite marks are so it shows on every print.
 */
internal fun myWordFills(
    geometry: PageGeometry,
    decorations: PageDecorations,
    field: AwnFieldColors,
): List<PageFill> {
    if (decorations.myWordMarks.isEmpty()) return emptyList()
    return wordSlotFills(geometry) { key ->
        decorations.myWordMarks[key]?.let { mark -> myWordFillColor(mark, field) }
    }
}

internal fun wordSlotFills(geometry: PageGeometry, colorOf: (WordKey) -> Color?): List<PageFill> =
    geometry.textLines.flatMap { line ->
        line.tokens.mapNotNull { placement ->
            val token = placement.token as? LineToken.WordToken ?: return@mapNotNull null
            val color = colorOf(token.key) ?: return@mapNotNull null
            val rect =
                Rect(placement.left, line.top, placement.right, line.top + geometry.slotHeightPx)
            PageFill(rect, color)
        }
    }
