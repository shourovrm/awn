// The numbers below are fractions of the glyph box, not sizes, so they are not design tokens.
@file:Suppress("MagicNumber")

package app.awn.quran.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.awn.quran.core.ui.theme.AwnColors

private val glyphColor: Color = AwnColors.ochre

internal fun DrawScope.drawPlayGlyph() {
    val triangle =
        Path().apply {
            moveTo(size.width * 0.2f, 0f)
            lineTo(size.width, size.height * 0.5f)
            lineTo(size.width * 0.2f, size.height)
            close()
        }
    drawPath(triangle, glyphColor)
}

internal fun DrawScope.drawPauseGlyph() {
    val barSize = Size(size.width * 0.3f, size.height)
    drawRect(glyphColor, topLeft = Offset(size.width * 0.1f, 0f), size = barSize)
    drawRect(glyphColor, topLeft = Offset(size.width * 0.6f, 0f), size = barSize)
}

internal fun DrawScope.drawExpandGlyph() {
    val chevron =
        Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.7f)
            lineTo(size.width * 0.5f, size.height * 0.3f)
            lineTo(size.width * 0.9f, size.height * 0.7f)
        }
    drawPath(chevron, glyphColor, style = Stroke(width = size.width * 0.12f))
}
