package app.awn.quran.feature.read.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnFieldColors
import kotlin.math.roundToInt

/** Where the middle of the ink of an Arabic line sits above its baseline, in ems. */
private const val INK_CENTER_ABOVE_BASELINE_EM = 0.35f

/**
 * One mushaf page: [layout] drawn like the print, right to left, with the page frame (surah name
 * and juz above, page number below) when [showPageInfo] is true.
 *
 * [fontFamily] is the font made for [layout]'s script and page, or null when it is not installed;
 * glyph scripts then draw the Unicode text with [plainFamily] (QPC Hafs) instead. The surah banner
 * and the Basmalah are drawn from Unicode text in every script, because the page fonts hold no
 * glyphs for them.
 *
 * A tap on a word calls [onWordClick]. A long press (500 ms) on a word or an ayah-end sign calls
 * [onAyahLongPress] with the ayah and the union of its line fragments, in the coordinates of the
 * window, so the caller can put a bar above it.
 */
@Composable
fun MushafPage(
    layout: PageLayout,
    chapters: List<Chapter>,
    fontFamily: FontFamily?,
    plainFamily: FontFamily?,
    modifier: Modifier = Modifier,
    decorations: PageDecorations = PageDecorations(),
    showPageInfo: Boolean = true,
    onWordClick: (WordKey) -> Unit = {},
    onAyahLongPress: (AyahRef, Rect) -> Unit = { _, _ -> },
) {
    val hasGlyphFont = fontFamily != null && layout.script in glyphScripts
    val rows = remember(layout, hasGlyphFont) { buildPageRows(layout, hasGlyphFont) }
    val textFamily = if (hasGlyphFont) fontFamily else plainFamily

    // The page is positioned from left to right in pixels; text direction is inside each word.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxSize()) {
            if (showPageInfo) PageHeaderRow(layout, chapters)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                PageBody(
                    layout = layout,
                    rows = rows,
                    chapters = chapters,
                    textFamily = textFamily,
                    plainFamily = plainFamily,
                    widthPx = with(density) { maxWidth.toPx() },
                    heightPx = with(density) { maxHeight.toPx() },
                    decorations = decorations,
                    onWordClick = onWordClick,
                    onAyahLongPress = onAyahLongPress,
                )
            }
            if (showPageInfo) PageNumberRow(layout.page)
        }
    }
}

@Composable
private fun PageBody(
    layout: PageLayout,
    rows: List<PageRow>,
    chapters: List<Chapter>,
    textFamily: FontFamily?,
    plainFamily: FontFamily?,
    widthPx: Float,
    heightPx: Float,
    decorations: PageDecorations,
    onWordClick: (WordKey) -> Unit,
    onAyahLongPress: (AyahRef, Rect) -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measured = rememberPageGeometry(layout, rows, textFamily, widthPx, heightPx)
    val geometry = measured.geometry
    val measurements = measured.measurements
    val field = Awn.field
    var containerPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { coordinates: LayoutCoordinates ->
                containerPosition = coordinates.positionInRoot()
            }
            .pageGestures(geometry, containerPosition, onWordClick, onAyahLongPress)
            .drawBehind { drawFills(geometry, decorations, field) },
    ) {
        val fontSizePx = geometry.fontSizePx
        val style = pageTextStyle(textFamily, fontSizePx, density)
        val baselineShift = measurements.firstBaselineEm * fontSizePx
        val inkLift = INK_CENTER_ABOVE_BASELINE_EM * fontSizePx

        for (row in rows) {
            when (row) {
                is PageRow.TextRow ->
                    TextLine(
                        placement = geometry.textLines.first { it.slot == row.slot },
                        geometry = geometry,
                        style = style,
                        topOfText = geometry.slotCenter(row.slot) + inkLift - baselineShift,
                        decorations = decorations,
                        field = field,
                    )
                is PageRow.HeaderRow ->
                    SurahBanner(
                        name = chapters.firstOrNull { it.number == row.surah }?.nameArabic,
                        geometry = geometry,
                        slot = row.slot,
                        family = plainFamily,
                        field = field,
                    )
                is PageRow.BasmalaRow ->
                    BasmalaLine(
                        geometry = geometry,
                        slot = row.slot,
                        family = plainFamily,
                        measurer = textMeasurer,
                        color = field.content,
                    )
            }
        }
    }
}

@Composable
private fun TextLine(
    placement: LinePlacement,
    geometry: PageGeometry,
    style: TextStyle,
    topOfText: Float,
    decorations: PageDecorations,
    field: AwnFieldColors,
) {
    val density = LocalDensity.current
    for (tokenPlacement in placement.tokens) {
        val slotModifier =
            Modifier.alignedToSlot(
                left = tokenPlacement.left,
                slotTop = placement.top,
                slotHeight = geometry.slotHeightPx,
                textTop = topOfText,
            )
        when (val token = tokenPlacement.token) {
            is LineToken.WordToken ->
                Text(
                    text = token.text,
                    style = style,
                    color = wordColor(decorations.wordStates(token.key), field),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = slotModifier,
                )
            is LineToken.RosetteToken ->
                if (token.glyphCode != null) {
                    Text(
                        text = token.glyphCode,
                        style = style,
                        color = field.content,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = slotModifier,
                    )
                } else {
                    AyahRing(
                        number = token.ayah.ayah,
                        fontSizePx = geometry.fontSizePx,
                        slotCenter = geometry.slotCenter(placement.slot),
                        left = tokenPlacement.left,
                        density = density,
                    )
                }
        }
    }
}

/**
 * Gives the text the size and place of its line slot, so the node a test or a screen reader finds
 * is the same rectangle as the highlight behind it, while the glyphs are drawn at [textTop]. The
 * text may reach beyond the slot (tall marks do); it is not clipped.
 */
private fun Modifier.alignedToSlot(
    left: Float,
    slotTop: Float,
    slotHeight: Float,
    textTop: Float,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
    layout(placeable.width, slotHeight.roundToInt()) {
        placeable.place(0, (textTop - slotTop).roundToInt())
    }
}
    .offset { IntOffset(left.roundToInt(), slotTop.roundToInt()) }

/** The placed page and the measurements it was placed with. */
private class MeasuredPage(val geometry: PageGeometry, val measurements: TokenMeasurements)

/**
 * Measures the tokens twice: first at a large size to find the font size that fits the page, then
 * at that size, so the placed rectangles are the widths the text really draws with.
 */
@Composable
private fun rememberPageGeometry(
    layout: PageLayout,
    rows: List<PageRow>,
    textFamily: FontFamily?,
    widthPx: Float,
    heightPx: Float,
): MeasuredPage {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val isCentredBlock = isCentredBlockPage(layout)
    val coarse =
        remember(rows, textFamily, density) {
            val style = pageTextStyle(textFamily, MEASURE_FONT_PX, density)
            measureTokens(rows, style, MEASURE_FONT_PX, textMeasurer)
        }
    val coarseSize =
        remember(rows, widthPx, heightPx, coarse) {
            layoutPage(rows, isCentredBlock, widthPx, heightPx, coarse::widthEm).fontSizePx
        }
    val measurements =
        remember(rows, textFamily, density, coarseSize) {
            val style = pageTextStyle(textFamily, coarseSize, density)
            measureTokens(rows, style, coarseSize, textMeasurer)
        }
    val geometry =
        remember(rows, widthPx, heightPx, measurements) {
            layoutPage(rows, isCentredBlock, widthPx, heightPx, measurements::widthEm)
        }
    return MeasuredPage(geometry, measurements)
}
