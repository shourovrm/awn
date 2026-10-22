package app.awn.quran.feature.read.page

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.tracker.WordState

// The hint letter fills about this much of the height of a text line.
private const val HINT_LETTER_SHARE = 0.6f

/** A Hidden word's block on the page, with the key the hint letter is looked up by. */
internal class HiddenWordSlot(val key: WordKey, val rect: Rect)

internal fun hiddenWordSlots(
    geometry: PageGeometry,
    decorations: PageDecorations,
): List<HiddenWordSlot> =
    geometry.textLines.flatMap { line ->
        line.tokens.mapNotNull { placement ->
            val token = placement.token as? LineToken.WordToken ?: return@mapNotNull null
            if (decorations.wordStates(token.key) != WordState.Hidden) return@mapNotNull null
            val rect =
                Rect(placement.left, line.top, placement.right, line.top + geometry.slotHeightPx)
            HiddenWordSlot(token.key, rect)
        }
    }

/**
 * The first letter of the next word, centred inside the block of that word. The block keeps its
 * size, so the line does not move when the letter appears.
 */
internal fun DrawScope.drawHintLetters(
    geometry: PageGeometry,
    decorations: PageDecorations,
    field: AwnFieldColors,
    textMeasurer: TextMeasurer,
    family: FontFamily?,
) {
    val style =
        TextStyle(
            color = field.content,
            fontFamily = family,
            fontSize = (geometry.slotHeightPx * HINT_LETTER_SHARE).toSp(),
        )
    for (slot in hiddenWordSlots(geometry, decorations)) {
        val letter = decorations.hintLetter(slot.key) ?: continue
        val measured = textMeasurer.measure(letter, style)
        val topLeft =
            Offset(
                slot.rect.center.x - measured.size.width / 2f,
                slot.rect.center.y - measured.size.height / 2f,
            )
        drawText(measured, topLeft = topLeft)
    }
}
