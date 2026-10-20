package app.awn.quran.feature.read.page

import androidx.compose.ui.geometry.Rect
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import kotlin.math.abs

/** Where one word or ayah-end sign sits on its line, in pixels from the left edge of the page. */
internal data class TokenPlacement(val token: LineToken, val left: Float, val right: Float)

/** One line of text with its tokens placed. [top] is the top of the line's slot. */
internal data class LinePlacement(val slot: Int, val top: Float, val tokens: List<TokenPlacement>)

/**
 * Where everything of one page is drawn. All numbers are pixels in the page's own coordinates (the
 * origin is its top left corner). The text block is [frameLeft] to [frameRight] wide; lines that
 * fill the print are justified to exactly that width.
 */
internal class PageGeometry(
    val fontSizePx: Float,
    val slotHeightPx: Float,
    private val firstSlotTop: Float,
    val frameLeft: Float,
    val frameRight: Float,
    val textLines: List<LinePlacement>,
) {
    fun slotTop(slot: Int): Float = firstSlotTop + (slot - 1) * slotHeightPx

    fun slotCenter(slot: Int): Float = slotTop(slot) + slotHeightPx / 2f

    /** The rectangles behind [ayah], one per line it has a word or end sign on, top to bottom. */
    fun ayahFragments(ayah: AyahRef): List<Rect> = textLines.mapNotNull { line ->
        val own = line.tokens.filter { placement -> ayahOf(placement.token) == ayah }
        if (own.isEmpty()) {
            null
        } else {
            Rect(
                left = own.minOf { it.left },
                top = line.top,
                right = own.maxOf { it.right },
                bottom = line.top + slotHeightPx,
            )
        }
    }

    /** The smallest rectangle that holds every fragment of [ayah], or null when it is not here. */
    fun ayahBounds(ayah: AyahRef): Rect? {
        val fragments = ayahFragments(ayah)
        if (fragments.isEmpty()) return null
        return Rect(
            left = fragments.minOf { it.left },
            top = fragments.minOf { it.top },
            right = fragments.maxOf { it.right },
            bottom = fragments.maxOf { it.bottom },
        )
    }

    /** The rectangle of [key], or null when that word is not on the page. */
    fun wordRect(key: WordKey): Rect? {
        for (line in textLines) {
            val placement =
                line.tokens.firstOrNull { (it.token as? LineToken.WordToken)?.key == key }
            if (placement != null) {
                return Rect(placement.left, line.top, placement.right, line.top + slotHeightPx)
            }
        }
        return null
    }

    /**
     * The token under the point, or null. The line is the one whose centre is nearest, as long as
     * the point is within [HIT_REACH_SLOTS] slots of it, so a tap between two lines still lands on
     * a word; a touch up to half a gap beside a token counts as on it, and the nearest token wins.
     */
    fun tokenAt(x: Float, y: Float): LineToken? {
        val line = textLines.minByOrNull { abs(it.top + slotHeightPx / 2f - y) }
        val isOnLine =
            line != null && abs(line.top + slotHeightPx / 2f - y) <= slotHeightPx * HIT_REACH_SLOTS
        val nearest = if (isOnLine) line?.tokens?.minByOrNull { distanceToToken(it, x) } else null
        val slop = fontSizePx * GAP_EM / 2f
        return nearest?.token?.takeIf { distanceToToken(nearest, x) <= slop }
    }

    private fun distanceToToken(placement: TokenPlacement, x: Float): Float =
        maxOf(placement.left - x, x - placement.right, 0f)

    private fun ayahOf(token: LineToken): AyahRef =
        when (token) {
            is LineToken.WordToken -> AyahRef(token.key.surah, token.key.ayah)
            is LineToken.RosetteToken -> token.ayah
        }

    companion object {
        /** The space between two words, in ems of the page font. */
        const val GAP_EM = 0.18f

        /** Lines at least this share of the text block are stretched to its full width. */
        const val FULL_LINE_SHARE = 0.8f

        /** How far from the middle of a line, in slots, a touch still counts as on the line. */
        const val HIT_REACH_SLOTS = 0.75f

        /** Normal pages have 15 line slots. */
        const val SLOTS = 15

        /** Pages 1 and 2: line spacing in slots, text block width, and where the banner sits. */
        const val CENTRED_SLOT_SCALE = 1.2f
        const val CENTRED_FRAME_SHARE = 0.9f
        const val CENTRED_BANNER_CENTER = 0.27f

        /** The glyph ink of a line stays inside this share of its slot height. */
        const val MAX_FONT_SHARE_OF_SLOT = 0.8f
    }
}

/**
 * Places the rows of one page. [tokenWidthEm] is the width of a word or end sign in ems of the page
 * font. The font size is the largest at which the widest line fits the text block; short last lines
 * of a surah stay short and centred, all others are justified to the block width.
 *
 * Pages 1 and 2 ([isCentredBlock]) keep the spacing of normal pages scaled up a little, put the
 * surah banner at 27 % of the height and centre every line; they are not stretched to 15 slots.
 */
internal fun layoutPage(
    rows: List<PageRow>,
    isCentredBlock: Boolean,
    widthPx: Float,
    heightPx: Float,
    tokenWidthEm: (LineToken) -> Float,
): PageGeometry {
    val textRows = rows.filterIsInstance<PageRow.TextRow>()
    val slotHeight =
        if (isCentredBlock) {
            heightPx / PageGeometry.SLOTS * PageGeometry.CENTRED_SLOT_SCALE
        } else {
            heightPx / PageGeometry.SLOTS
        }
    val firstSlotTop =
        if (isCentredBlock) {
            heightPx * PageGeometry.CENTRED_BANNER_CENTER - slotHeight / 2f
        } else {
            0f
        }
    val availableWidth = if (isCentredBlock) widthPx * PageGeometry.CENTRED_FRAME_SHARE else widthPx

    val naturalEm = textRows.associate { row -> row.slot to naturalWidthEm(row, tokenWidthEm) }
    val widestEm = naturalEm.values.maxOrNull() ?: 1f
    val fitToWidth = availableWidth / widestEm
    val fitToHeight = slotHeight * PageGeometry.MAX_FONT_SHARE_OF_SLOT
    val fontSize = minOf(fitToWidth, fitToHeight)
    val frameWidth = minOf(availableWidth, widestEm * fontSize)
    val frameLeft = (widthPx - frameWidth) / 2f
    val frameRight = frameLeft + frameWidth

    val lines = textRows.map { row ->
        val top = firstSlotTop + (row.slot - 1) * slotHeight
        val isFull =
            !isCentredBlock &&
                naturalEm.getValue(row.slot) * fontSize >= frameWidth * PageGeometry.FULL_LINE_SHARE
        LinePlacement(
            row.slot,
            top,
            placeTokens(row, fontSize, LineFrame(frameLeft, frameRight, isFull), tokenWidthEm),
        )
    }
    return PageGeometry(
        fontSizePx = fontSize,
        slotHeightPx = slotHeight,
        firstSlotTop = firstSlotTop,
        frameLeft = frameLeft,
        frameRight = frameRight,
        textLines = lines,
    )
}

private fun naturalWidthEm(row: PageRow.TextRow, tokenWidthEm: (LineToken) -> Float): Float {
    val gaps = (row.tokens.size - 1).coerceAtLeast(0)
    return row.tokens.sumOf { tokenWidthEm(it).toDouble() }.toFloat() + gaps * PageGeometry.GAP_EM
}

/** The horizontal extent of the text block and whether a line is stretched to fill it. */
private data class LineFrame(val left: Float, val right: Float, val isJustified: Boolean)

/** Places tokens right to left: the first token of the line is at the right edge. */
private fun placeTokens(
    row: PageRow.TextRow,
    fontSize: Float,
    frame: LineFrame,
    tokenWidthEm: (LineToken) -> Float,
): List<TokenPlacement> {
    val widths = row.tokens.map { tokenWidthEm(it) * fontSize }
    val gapCount = (row.tokens.size - 1).coerceAtLeast(0)
    val wordsWidth = widths.sum()
    val canStretch = frame.isJustified && gapCount > 0
    val gap =
        if (canStretch) {
            (frame.right - frame.left - wordsWidth) / gapCount
        } else {
            fontSize * PageGeometry.GAP_EM
        }
    val lineWidth = wordsWidth + gap * gapCount
    var right = if (canStretch) frame.right else (frame.left + frame.right + lineWidth) / 2f
    return row.tokens.mapIndexed { index, token ->
        val placement = TokenPlacement(token, left = right - widths[index], right = right)
        right -= widths[index] + gap
        placement
    }
}
