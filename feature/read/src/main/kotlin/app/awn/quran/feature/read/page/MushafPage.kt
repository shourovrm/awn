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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
 * A tap anywhere on the page calls [onTap] the moment the finger lifts; a tap on a word also calls
 * [onWordClick]. A long press (500 ms) anywhere, even in a margin, calls [onAyahLongPress] with the
 * nearest ayah; dragging on calls [onRangeDrag] with the range from that ayah to the one under the
 * finger, and lifting calls [onRangeEnd]. [onSelectionBounds] reports where the ayahs in
 * [decorations] are, in this page's own coordinates (origin at its top left), or null when none of
 * them is on this page.
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
    onTap: () -> Unit = {},
    onAyahLongPress: (AyahRef) -> Unit = {},
    onRangeDrag: (AyahRange) -> Unit = {},
    onRangeEnd: () -> Unit = {},
    onSelectionBounds: (SelectionBounds?) -> Unit = {},
) {
    val hasGlyphFont = fontFamily != null && layout.script in glyphScripts
    val rows = remember(layout, hasGlyphFont) { buildPageRows(layout, hasGlyphFont) }
    val textFamily = if (hasGlyphFont) fontFamily else plainFamily
    var bodyTop by remember { mutableFloatStateOf(0f) }
    val touchActions =
        rememberTouchActions(onWordClick, onTap, onAyahLongPress, onRangeDrag, onRangeEnd)

    // The page is positioned from left to right in pixels; text direction is inside each word.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxSize()) {
            if (showPageInfo) PageHeaderRow(layout, chapters)
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                    bodyTop = it.positionInParent().y
                },
            ) {
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
                    touchActions = touchActions,
                    bodyTop = bodyTop,
                    onSelectionBounds = onSelectionBounds,
                )
            }
            if (showPageInfo) PageNumberRow(layout.page)
        }
    }
}

/**
 * The touch actions of a page, built once so a recomposition during a drag does not restart the
 * gesture; each call reaches the newest callback.
 */
@Composable
private fun rememberTouchActions(
    onWordClick: (WordKey) -> Unit,
    onTap: () -> Unit,
    onAyahLongPress: (AyahRef) -> Unit,
    onRangeDrag: (AyahRange) -> Unit,
    onRangeEnd: () -> Unit,
): PageTouchActions {
    val currentOnWordClick by rememberUpdatedState(onWordClick)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onAyahLongPress)
    val currentOnRangeDrag by rememberUpdatedState(onRangeDrag)
    val currentOnRangeEnd by rememberUpdatedState(onRangeEnd)
    return remember {
        PageTouchActions(
            onTap = { currentOnTap() },
            onWordClick = { currentOnWordClick(it) },
            onLongPress = { currentOnLongPress(it) },
            onRangeDrag = { currentOnRangeDrag(it) },
            onRangeEnd = { currentOnRangeEnd() },
        )
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
    touchActions: PageTouchActions,
    bodyTop: Float,
    onSelectionBounds: (SelectionBounds?) -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measured = rememberPageGeometry(layout, rows, textFamily, widthPx, heightPx)
    val geometry = measured.geometry
    val measurements = measured.measurements
    val field = Awn.field
    ReportSelectionBounds(geometry, decorations.selectedAyahs, bodyTop, onSelectionBounds)
    val playingFade = rememberPlayingFade(decorations.playingAyah)

    Box(
        Modifier.fillMaxSize().pageGestures(geometry, touchActions).drawBehind {
            drawFills(geometry, decorations, field, playingFade.snapshot())
        },
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
        // What is drawn may be private-use glyph codes; screen readers get the Unicode text here.
        AyahTextLayer(
            layout = layout,
            geometry = geometry,
            chapters = chapters,
            onSelect = { ayah ->
                touchActions.onLongPress(ayah)
                touchActions.onRangeEnd()
            },
        )
    }
}

/** Tells the screen where the selected ayahs are whenever the selection or the layout changes. */
@Composable
private fun ReportSelectionBounds(
    geometry: PageGeometry,
    selectedAyahs: Set<AyahRef>,
    bodyTop: Float,
    onSelectionBounds: (SelectionBounds?) -> Unit,
) {
    val currentOnSelectionBounds by rememberUpdatedState(onSelectionBounds)
    LaunchedEffect(geometry, selectedAyahs, bodyTop) {
        currentOnSelectionBounds(
            geometry.selectionBounds(selectedAyahs)?.translate(Offset(0f, bodyTop)),
        )
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
                    modifier = slotModifier.hiddenFromScreenReaders(),
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
                        modifier = slotModifier.hiddenFromScreenReaders(),
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
