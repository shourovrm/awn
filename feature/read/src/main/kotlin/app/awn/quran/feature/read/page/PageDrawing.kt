package app.awn.quran.feature.read.page

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.core.ui.theme.AwnReciteFills
import app.awn.quran.tracker.WordState
import kotlin.math.roundToInt

internal const val HIDDEN_BLOCK_ALPHA = 0.3f
internal const val BASMALA_WIDTH_SHARE = 0.62f
internal const val BASMALA_MAX_SIZE_SHARE = 1.25f
internal const val BANNER_HEIGHT_SHARE = 0.78f
internal const val BANNER_TEXT_SHARE = 0.46f
internal const val RING_SIZE_EM = 0.9f
internal const val FILL_INSET_SHARE = 0.04f
/** The text in the bismillah line; it is not part of the word tables. */
internal const val BASMALA_TEXT = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"
/** The word the banner puts before the surah name. */
internal const val BANNER_PREFIX = "سُورَةُ "

/** The ring that stands for the ayah-end sign when no page font draws the printed one. */
@Composable
internal fun AyahRing(
    number: Int,
    fontSizePx: Float,
    slotCenter: Float,
    left: Float,
    density: Density,
) {
    val ringPx = RING_SIZE_EM * fontSizePx
    val widthPx = RING_WIDTH_EM * fontSizePx
    val ringDp = with(density) { ringPx.toDp() }
    Box(
        modifier =
            Modifier.offset {
                    IntOffset(
                        (left + (widthPx - ringPx) / 2f).roundToInt(),
                        (slotCenter - ringPx / 2f).roundToInt(),
                    )
                }
                .size(ringDp)
                .border(1.dp, Awn.field.tentativeWord, CircleShape)
                .hiddenFromScreenReaders(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = toArabicIndicDigits(number),
            style = TextStyle(fontSize = with(density) { (ringPx * RING_DIGIT_SHARE).toSp() }),
            color = Awn.field.content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

internal const val RING_DIGIT_SHARE = 0.42f

@Composable
internal fun SurahBanner(
    name: String?,
    geometry: PageGeometry,
    slot: Int,
    family: FontFamily?,
    field: AwnFieldColors,
) {
    val density = LocalDensity.current
    val widthPx = geometry.frameRight - geometry.frameLeft
    val heightPx = geometry.slotHeightPx * BANNER_HEIGHT_SHARE
    val top = geometry.slotCenter(slot) - heightPx / 2f
    val frameColor = field.buttonFill
    Box(
        modifier =
            Modifier.offset { IntOffset(geometry.frameLeft.roundToInt(), top.roundToInt()) }
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .drawBehind { drawBannerFrame(frameColor) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = BANNER_PREFIX + name.orEmpty(),
            style =
                TextStyle(
                    fontFamily = family,
                    fontSize = with(density) { (heightPx * BANNER_TEXT_SHARE).toSp() },
                    textAlign = TextAlign.Center,
                ),
            color = field.content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/** A double frame with a diamond at each end, the nearest simple shape to the printed banner. */
internal fun DrawScope.drawBannerFrame(color: Color) {
    val outerStroke = Stroke(width = 2.dp.toPx())
    val innerStroke = Stroke(width = 1.dp.toPx())
    val corner = CornerRadius(size.height * BANNER_CORNER_SHARE)
    drawRoundRect(color, size = size, cornerRadius = corner, style = outerStroke)
    val gap = size.height * BANNER_INNER_GAP_SHARE
    drawRoundRect(
        color,
        topLeft = Offset(gap, gap),
        size = Size(size.width - gap * 2f, size.height - gap * 2f),
        cornerRadius = corner,
        style = innerStroke,
    )
    val diamond = size.height * BANNER_DIAMOND_SHARE
    for (centerX in listOf(gap + diamond * 2f, size.width - gap - diamond * 2f)) {
        val path =
            Path().apply {
                moveTo(centerX, size.height / 2f - diamond)
                lineTo(centerX + diamond, size.height / 2f)
                lineTo(centerX, size.height / 2f + diamond)
                lineTo(centerX - diamond, size.height / 2f)
                close()
            }
        drawPath(path, color)
    }
}

internal const val BANNER_CORNER_SHARE = 0.22f
internal const val BANNER_INNER_GAP_SHARE = 0.12f
internal const val BANNER_DIAMOND_SHARE = 0.11f

@Composable
internal fun BasmalaLine(
    geometry: PageGeometry,
    slot: Int,
    family: FontFamily?,
    widthEm: Float,
    color: Color,
) {
    val density = LocalDensity.current
    val frameWidth = geometry.frameRight - geometry.frameLeft
    val fontSize =
        minOf(
            frameWidth * BASMALA_WIDTH_SHARE / widthEm,
            geometry.fontSizePx * BASMALA_MAX_SIZE_SHARE,
        )
    Box(
        Modifier.offset {
                IntOffset(geometry.frameLeft.roundToInt(), geometry.slotTop(slot).roundToInt())
            }
            .size(
                with(density) { frameWidth.toDp() },
                with(density) { geometry.slotHeightPx.toDp() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = BASMALA_TEXT,
            style = pageTextStyle(family, fontSize, density),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/** A rectangle to paint behind the text and its colour. */
internal data class PageFill(val rect: Rect, val color: Color)

/**
 * How the playing-ayah highlight changes hands: the ayah that was playing fades out at
 * [outgoingAlpha] while the new one fades in at [incomingAlpha], so the new ayah is lit before the
 * old one is gone.
 */
internal data class PlayingFade(
    val incoming: AyahRef?,
    val incomingAlpha: Float = 1f,
    val outgoing: AyahRef? = null,
    val outgoingAlpha: Float = 0f,
)

/** The fills behind the ayah line fragments, lowest layer first: bookmarks up to the word. */
internal fun ayahFills(
    geometry: PageGeometry,
    decorations: PageDecorations,
    field: AwnFieldColors,
    playingFade: PlayingFade = PlayingFade(decorations.playingAyah),
): List<PageFill> {
    fun fragments(ayah: AyahRef, color: Color, alpha: Float = 1f) =
        geometry.ayahFragments(ayah).map { PageFill(it, color.copy(alpha = color.alpha * alpha)) }

    val bookmarked = decorations.bookmarkedAyahs.flatMap { fragments(it, field.bookmarkedAyahFill) }
    val selected = decorations.selectedAyahs.flatMap { fragments(it, field.selectedAyahFill) }
    val outgoing =
        playingFade.outgoing?.let {
            fragments(it, field.playingAyahFill, playingFade.outgoingAlpha)
        }
    val incoming =
        playingFade.incoming?.let {
            fragments(it, field.playingAyahFill, playingFade.incomingAlpha)
        }
    val word =
        decorations.playingWord?.let { key ->
            geometry.wordRect(key)?.let { listOf(PageFill(it, field.playingWordFill)) }
        }
    return bookmarked + selected + outgoing.orEmpty() + incoming.orEmpty() + word.orEmpty()
}

/** One block over every word whose state is Hidden. */
internal fun hiddenWordFills(
    geometry: PageGeometry,
    decorations: PageDecorations,
    field: AwnFieldColors,
): List<PageFill> =
    geometry.textLines.flatMap { line ->
        line.tokens
            .filter { placement ->
                val token = placement.token
                token is LineToken.WordToken &&
                    decorations.wordStates(token.key) == WordState.Hidden
            }
            .map { placement ->
                val rect =
                    Rect(
                        placement.left,
                        line.top,
                        placement.right,
                        line.top + geometry.slotHeightPx,
                    )
                PageFill(rect, field.content.copy(alpha = HIDDEN_BLOCK_ALPHA))
            }
    }

/**
 * The soft fill behind each word whose recitation state is Confirmed, Mispronounced or Wrong.
 * Colour glyph fonts ignore the text colour, so a fill is the one mark that shows on every print.
 */
internal fun wordStateFills(
    geometry: PageGeometry,
    decorations: PageDecorations,
    field: AwnFieldColors,
): List<PageFill> =
    geometry.textLines.flatMap { line ->
        line.tokens.mapNotNull { placement ->
            val token = placement.token as? LineToken.WordToken ?: return@mapNotNull null
            val color =
                reciteFillColor(decorations.wordStates(token.key), field) ?: return@mapNotNull null
            val rect =
                Rect(placement.left, line.top, placement.right, line.top + geometry.slotHeightPx)
            PageFill(rect, color)
        }
    }

/** The fill colour for [state] on [field], or null when the state is drawn some other way. */
internal fun reciteFillColor(state: WordState, field: AwnFieldColors): Color? =
    if (field.isLight) {
        when (state) {
            WordState.Confirmed -> AwnReciteFills.dayConfirmed
            WordState.Mispronounced -> AwnReciteFills.dayTentative
            WordState.Wrong -> AwnReciteFills.dayWrong
            else -> null
        }
    } else {
        when (state) {
            WordState.Confirmed -> AwnReciteFills.nightConfirmed
            WordState.Mispronounced -> AwnReciteFills.nightTentative
            WordState.Wrong -> AwnReciteFills.nightWrong
            else -> null
        }
    }

/** Paints the fills behind the page text. */
internal fun DrawScope.drawFills(
    geometry: PageGeometry,
    decorations: PageDecorations,
    field: AwnFieldColors,
    playingFade: PlayingFade = PlayingFade(decorations.playingAyah),
) {
    val corner = CornerRadius(4.dp.toPx())
    val inset = geometry.slotHeightPx * FILL_INSET_SHARE
    val fills =
        ayahFills(geometry, decorations, field, playingFade) +
            wordStateFills(geometry, decorations, field) +
            hiddenWordFills(geometry, decorations, field)
    for ((rect, color) in fills) {
        drawRoundRect(
            color,
            topLeft = Offset(rect.left, rect.top + inset),
            size = Size(rect.width, rect.height - inset * 2f),
            cornerRadius = corner,
        )
    }
    focusedWordRect(geometry, decorations)?.let { rect ->
        val ringTopLeft = Offset(rect.left, rect.top + inset)
        val ringSize = Size(rect.width, rect.height - inset * 2f)
        drawRoundRect(field.selectedAyahFill, ringTopLeft, ringSize, corner)
        drawRoundRect(
            field.buttonFill,
            ringTopLeft,
            ringSize,
            corner,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/**
 * The ink of a word. Recitation states are shown by fills behind the word ([wordStateFills]), so
 * the ink stays readable on every print; only a Hidden word is blanked, under its block.
 */
internal fun wordColor(state: WordState, field: AwnFieldColors): Color =
    if (state == WordState.Hidden) Color.Transparent else field.content
