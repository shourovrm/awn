package app.awn.quran.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnSpacing

private val BLOCK_CORNER_RADIUS = AwnSpacing.matchBlockCorner
private val BLOCK_HORIZONTAL_PADDING = 2.dp

/**
 * Text with a rounded block behind each of [matches]. A text span's own background cannot round its
 * corners and fills the whole line box, which for Arabic with tall line spacing is a slab, so the
 * blocks are drawn from the text layout instead. The text itself keeps one style from end to end,
 * so a highlight never splits the shaping of a letter from its marks.
 *
 * [blockHeightFraction] is the height of a block as a share of the line height.
 */
@Composable
internal fun MatchHighlightedText(
    text: String,
    matches: List<IntRange>,
    blockColor: Color,
    blockHeightFraction: Float,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier =
            modifier.drawBehind {
                val measured = layout ?: return@drawBehind
                val padding = BLOCK_HORIZONTAL_PADDING.toPx()
                val radius = CornerRadius(BLOCK_CORNER_RADIUS.toPx())
                matches
                    .flatMap { match -> matchBlocks(measured, match, blockHeightFraction) }
                    .forEach { block ->
                        drawRoundRect(
                            color = blockColor,
                            topLeft = Offset(block.left - padding, block.top),
                            size = Size(block.width + 2 * padding, block.height),
                            cornerRadius = radius,
                        )
                    }
            },
    )
}

/**
 * The rectangles that cover [match] in [layout], one per line the match crosses, each
 * [heightFraction] of the line height tall and centred on the line. Characters hidden by the
 * ellipsis of a shortened text have no rectangle.
 */
internal fun matchBlocks(
    layout: TextLayoutResult,
    match: IntRange,
    heightFraction: Float,
): List<Rect> {
    val textLength = layout.layoutInput.text.length
    val start = match.first.coerceIn(0, textLength)
    val endExclusive = (match.last + 1).coerceIn(0, textLength)
    if (start >= endExclusive) return emptyList()
    val blocks = mutableListOf<Rect>()
    for (line in layout.getLineForOffset(start)..layout.getLineForOffset(endExclusive - 1)) {
        val lineStart = maxOf(start, layout.getLineStart(line))
        val lineEnd = minOf(endExclusive, layout.getLineEnd(line, visibleEnd = true))
        if (lineStart < lineEnd) {
            blocks += blockOnLine(layout, line, lineStart, lineEnd, heightFraction)
        }
    }
    return blocks
}

/** The block for the characters [from] until [until], all on [line]. */
private fun blockOnLine(
    layout: TextLayoutResult,
    line: Int,
    from: Int,
    until: Int,
    heightFraction: Float,
): Rect {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    for (offset in from until until) {
        val box = layout.getBoundingBox(offset)
        left = minOf(left, box.left)
        right = maxOf(right, box.right)
    }
    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)
    val blockHeight = (lineBottom - lineTop) * heightFraction
    val blockTop = (lineTop + lineBottom - blockHeight) / 2
    return Rect(left, blockTop, right, blockTop + blockHeight)
}
