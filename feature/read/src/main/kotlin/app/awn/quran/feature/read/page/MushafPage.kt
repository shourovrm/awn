package app.awn.quran.feature.read.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
 * [scale] (1 or more, default 1) makes the printed page that many times taller than the screen
 * body: the lines keep their breaks, the glyphs grow with the scale as far as the widest line fits
 * the screen width (see [layoutPage]), and the body scrolls vertically while the header and the
 * page number stay in place. Horizontal drags still reach the pager.
 *
 * A tap anywhere on the page calls [onTap] the moment the finger lifts; a tap on a word also calls
 * [onWordClick]. A long press (500 ms) anywhere, even in a margin, calls [onAyahLongPress] with the
 * nearest ayah; dragging on calls [onRangeDrag] with the range from that ayah to the one under the
 * finger, and lifting calls [onRangeEnd]. [onSelectionBounds] reports where the ayahs in
 * [decorations] are, in this page's own coordinates (origin at its top left), or null when none of
 * them is on this page. [onTrackedWordBounds] does the same for [trackedWord], the word the screen
 * wants to keep above its bottom bar.
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
    scale: Float = 1f,
    onWordClick: (WordKey) -> Unit = {},
    onWordTap: (WordKey, Rect) -> Unit = { _, _ -> },
    onTap: () -> Unit = {},
    onAyahLongPress: (AyahRef) -> Unit = {},
    onRangeDrag: (AyahRange) -> Unit = {},
    onRangeEnd: () -> Unit = {},
    onSelectionBounds: (SelectionBounds?) -> Unit = {},
    trackedWord: WordKey? = null,
    onTrackedWordBounds: (Rect?) -> Unit = {},
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
                val pageScale = scale.coerceAtLeast(1f)
                val widthPx = with(density) { maxWidth.toPx() }
                val bodyHeightPx = with(density) { maxHeight.toPx() }
                val pageHeightPx = pageHeightFor(widthPx, bodyHeightPx, pageScale)
                val isScrolling = pageHeightPx > bodyHeightPx + 1f
                val scrollState = rememberScrollState()
                val pageSize =
                    PagePlacement(
                        widthPx = widthPx,
                        pageHeightPx = pageHeightPx,
                        scale = pageScale,
                        isCentredBlock = isCentredBlockPage(layout),
                        isScrolling = isScrolling,
                    )
                // Only a page taller than the screen scrolls, so the portrait page at step 0 has
                // no scroll container at all.
                val scrollModifier =
                    if (isScrolling) Modifier.verticalScroll(scrollState) else Modifier
                Box(Modifier.fillMaxSize().then(scrollModifier)) {
                    PageBody(
                        layout = layout,
                        rows = rows,
                        hasGlyphFont = hasGlyphFont,
                        chapters = chapters,
                        textFamily = textFamily,
                        plainFamily = plainFamily,
                        size = pageSize,
                        decorations = decorations,
                        touchActions = touchActions,
                        bodyTop = bodyTop,
                        scrollOffsetPx = { scrollState.value },
                        onSelectionBounds = onSelectionBounds,
                        trackedWord = trackedWord,
                        onTrackedWordBounds = onTrackedWordBounds,
                        onWordTap = onWordTap,
                    )
                }
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
    hasGlyphFont: Boolean,
    chapters: List<Chapter>,
    textFamily: FontFamily?,
    plainFamily: FontFamily?,
    size: PagePlacement,
    decorations: PageDecorations,
    touchActions: PageTouchActions,
    bodyTop: Float,
    scrollOffsetPx: () -> Int,
    onSelectionBounds: (SelectionBounds?) -> Unit,
    trackedWord: WordKey?,
    onTrackedWordBounds: (Rect?) -> Unit,
    onWordTap: (WordKey, Rect) -> Unit,
) {
    val density = LocalDensity.current
    val measured =
        rememberMeasuredPage(
            layout,
            rows,
            hasGlyphFont,
            PageFamilies(textFamily, plainFamily),
            size,
        )
    if (measured == null) {
        // The page is being measured off the main thread; nothing is drawn for a frame or two.
        Box(Modifier.fillMaxSize())
        return
    }
    val geometry = measured.geometry
    val field = Awn.field
    ReportSelectionBounds(
        geometry,
        decorations.selectedAyahs,
        bodyTop,
        scrollOffsetPx,
        onSelectionBounds,
    )
    ReportTrackedWordBounds(geometry, trackedWord, bodyTop, scrollOffsetPx, onTrackedWordBounds)
    val playingFade = rememberPlayingFade(decorations.playingAyah)
    val gestures =
        rememberWordTapGestures(touchActions, geometry, bodyTop, scrollOffsetPx, onWordTap)

    val pageHeight = with(density) { size.pageHeightPx.toDp() }
    val heightModifier =
        if (size.isScrolling) Modifier.height(pageHeight) else Modifier.fillMaxHeight()
    Box(
        Modifier.fillMaxWidth().then(heightModifier).pageGestures(geometry, gestures).drawBehind {
            drawFills(geometry, decorations, field, playingFade.snapshot())
        },
    ) {
        PageRowsLayer(
            rows = measured.rows,
            measured = measured,
            families = measured.families,
            chapters = chapters,
            decorations = decorations,
        )
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

/** The page's touch actions, with each word tap reported with its place in the screen body. */
@Composable
private fun rememberWordTapGestures(
    touchActions: PageTouchActions,
    geometry: PageGeometry,
    bodyTop: Float,
    scrollOffsetPx: () -> Int,
    onWordTap: (WordKey, Rect) -> Unit,
): PageTouchActions {
    // Like the selection bounds, the rectangle is shifted into screen-body coordinates.
    val currentBodyTop by rememberUpdatedState(bodyTop)
    val currentWordTap by rememberUpdatedState(onWordTap)
    return remember(touchActions, geometry) {
        touchActions.withWordTapReport { key ->
            val rect = geometry.wordRect(key) ?: return@withWordTapReport
            val shift = Offset(0f, currentBodyTop - scrollOffsetPx())
            currentWordTap(key, rect.translate(shift))
        }
    }
}

/** Tells the screen where the selected ayahs are whenever the selection or the layout changes. */
@Composable
private fun ReportSelectionBounds(
    geometry: PageGeometry,
    selectedAyahs: Set<AyahRef>,
    bodyTop: Float,
    scrollOffsetPx: () -> Int,
    onSelectionBounds: (SelectionBounds?) -> Unit,
) {
    val currentOnSelectionBounds by rememberUpdatedState(onSelectionBounds)
    LaunchedEffect(geometry, selectedAyahs, bodyTop) {
        // The bounds are reported relative to the screen body, so a scrolled page moves them.
        snapshotFlow { scrollOffsetPx() }
            .collect { scrolled ->
                currentOnSelectionBounds(
                    geometry
                        .selectionBounds(selectedAyahs)
                        ?.translate(Offset(0f, bodyTop - scrolled)),
                )
            }
    }
}

/** Tells the screen where [trackedWord] is, in the same coordinates as the selection bounds. */
@Composable
private fun ReportTrackedWordBounds(
    geometry: PageGeometry,
    trackedWord: WordKey?,
    bodyTop: Float,
    scrollOffsetPx: () -> Int,
    onTrackedWordBounds: (Rect?) -> Unit,
) {
    val currentOnBounds by rememberUpdatedState(onTrackedWordBounds)
    LaunchedEffect(geometry, trackedWord, bodyTop) {
        snapshotFlow { scrollOffsetPx() }
            .collect { scrolled ->
                currentOnBounds(
                    trackedWord?.let(geometry::wordRect)?.translate(Offset(0f, bodyTop - scrolled)),
                )
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

/** The banner, Basmalah and text lines of a page, each at the place its row has in the geometry. */
@Composable
private fun PageRowsLayer(
    rows: List<PageRow>,
    measured: MeasuredPage,
    families: PageFamilies,
    chapters: List<Chapter>,
    decorations: PageDecorations,
) {
    val density = LocalDensity.current
    val field = Awn.field
    val geometry = measured.geometry
    val fontSizePx = geometry.fontSizePx
    val style = pageTextStyle(families.text, fontSizePx, density)
    val baselineShift = measured.measurements.firstBaselineEm * fontSizePx
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
                    family = families.plain,
                    field = field,
                )
            is PageRow.BasmalaRow ->
                BasmalaLine(
                    geometry = geometry,
                    slot = row.slot,
                    family = families.plain,
                    widthEm = checkNotNull(measured.basmalaWidthEm),
                    color = field.content,
                )
        }
    }
}
