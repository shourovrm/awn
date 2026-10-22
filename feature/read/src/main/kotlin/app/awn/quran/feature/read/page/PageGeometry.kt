package app.awn.quran.feature.read.page

import androidx.compose.ui.geometry.Offset
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

    /**
     * The ayah a touch at ([x], [y]) means, even in a margin or between lines: the ayah under the
     * point; else the ayah of the nearest line's token nearest to the point. Null only when the
     * page has no text. Used by the long press, which must always find an ayah.
     */
    fun nearestAyahAt(x: Float, y: Float): AyahRef? {
        val nearestLine = textLines.minByOrNull {
            maxOf(it.top - y, y - (it.top + slotHeightPx), 0f)
        }
        val nearestToken = nearestLine?.tokens?.minByOrNull { distanceToToken(it, x) }
        return nearestToken?.let { ayahOf(it.token) }
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

        /**
         * The narrowest gap between two words, in ems. Enlarging the page squeezes the gaps of the
         * widest lines down to this, and no further, so the words never touch.
         */
        const val MIN_GAP_EM = 0.04f
    }
}

/** Height over width of a printed Madani page, used when the screen is wider than it is tall. */
internal const val LANDSCAPE_PAGE_ASPECT = 1.5f

/**
 * The height of the whole page at the default size, in pixels. In portrait the page is as tall as
 * the screen body, which fits the width on a phone. In landscape the body is too short for 15 lines
 * at the width of the screen, so the page keeps the proportions of the printed page ([widthPx]
 * times [LANDSCAPE_PAGE_ASPECT]) and the body scrolls; fitting the page to the height would print a
 * narrow block in the middle of the screen.
 */
internal fun pageHeightFor(widthPx: Float, bodyHeightPx: Float): Float =
    if (widthPx > bodyHeightPx) maxOf(bodyHeightPx, widthPx * LANDSCAPE_PAGE_ASPECT)
    else bodyHeightPx

/** The width the text block may use: pages 1 and 2 keep a margin on both sides. */
internal fun availableWidthOf(widthPx: Float, isCentredBlock: Boolean): Float =
    if (isCentredBlock) widthPx * PageGeometry.CENTRED_FRAME_SHARE else widthPx

/** The height of one line slot and where the first slot starts, both in pixels. */
internal data class SlotGrid(val slotHeightPx: Float, val firstSlotTopPx: Float) {
    companion object {
        /**
         * The grid of a page [heightPx] tall at the default size: 15 equal slots, or for pages 1
         * and 2 slots 1.2 times taller with the banner centred at 27 % of the height.
         */
        fun forPageHeight(
            heightPx: Float,
            isCentredBlock: Boolean,
            slotCount: Int = PageGeometry.SLOTS,
        ): SlotGrid =
            if (isCentredBlock) {
                val slotHeight = heightPx / PageGeometry.SLOTS * PageGeometry.CENTRED_SLOT_SCALE
                SlotGrid(
                    slotHeightPx = slotHeight,
                    firstSlotTopPx =
                        heightPx * PageGeometry.CENTRED_BANNER_CENTER - slotHeight / 2f,
                )
            } else {
                SlotGrid(slotHeightPx = heightPx / slotCount, firstSlotTopPx = 0f)
            }
    }
}

/**
 * Places the rows of one page at the default size. [tokenWidthEm] is the width of a word or end
 * sign in ems of the page font. The font size is the largest at which the widest line fits the text
 * block; short last lines of a surah stay short and centred, all others are justified to the block
 * width.
 *
 * Pages 1 and 2 ([isCentredBlock]) keep the spacing of normal pages scaled up a little, put the
 * surah banner at 27 % of the height and centre every line; they are not stretched to 15 slots.
 */
@Suppress("LongParameterList") // Pure placement; the tests call it with these arguments.
internal fun layoutPage(
    rows: List<PageRow>,
    isCentredBlock: Boolean,
    widthPx: Float,
    heightPx: Float,
    tokenWidthEm: (LineToken) -> Float,
    slotCount: Int = PageGeometry.SLOTS,
): PageGeometry {
    val grid = SlotGrid.forPageHeight(heightPx, isCentredBlock, slotCount)
    val widestEm =
        rows.filterIsInstance<PageRow.TextRow>().maxOfOrNull { row ->
            naturalWidthEm(row, tokenWidthEm)
        } ?: 1f
    val fontSize =
        minOf(
            availableWidthOf(widthPx, isCentredBlock) / widestEm,
            grid.slotHeightPx * PageGeometry.MAX_FONT_SHARE_OF_SLOT,
        )
    return placeLines(rows, isCentredBlock, widthPx, grid, fontSize, tokenWidthEm)
}

/**
 * Places the text rows on [grid] with the font [fontSizePx], whatever the size: the block is as
 * wide as the widest line, but never wider than the page allows, and centred on the page.
 */
@Suppress("LongParameterList") // Pure placement; the tests call it with these arguments.
internal fun placeLines(
    rows: List<PageRow>,
    isCentredBlock: Boolean,
    widthPx: Float,
    grid: SlotGrid,
    fontSizePx: Float,
    tokenWidthEm: (LineToken) -> Float,
): PageGeometry {
    val textRows = rows.filterIsInstance<PageRow.TextRow>()
    val naturalEm = textRows.associate { row -> row.slot to naturalWidthEm(row, tokenWidthEm) }
    val widestEm = naturalEm.values.maxOrNull() ?: 1f
    val frameWidth = minOf(availableWidthOf(widthPx, isCentredBlock), widestEm * fontSizePx)
    val frameLeft = (widthPx - frameWidth) / 2f
    val frameRight = frameLeft + frameWidth

    val lines = textRows.map { row ->
        val top = grid.firstSlotTopPx + (row.slot - 1) * grid.slotHeightPx
        val isFull =
            !isCentredBlock &&
                naturalEm.getValue(row.slot) * fontSizePx >=
                    frameWidth * PageGeometry.FULL_LINE_SHARE
        LinePlacement(
            row.slot,
            top,
            placeTokens(row, fontSizePx, LineFrame(frameLeft, frameRight, isFull), tokenWidthEm),
        )
    }
    return PageGeometry(
        fontSizePx = fontSizePx,
        slotHeightPx = grid.slotHeightPx,
        firstSlotTop = grid.firstSlotTopPx,
        frameLeft = frameLeft,
        frameRight = frameRight,
        textLines = lines,
    )
}

private fun naturalWidthEm(
    row: PageRow.TextRow,
    tokenWidthEm: (LineToken) -> Float,
    gapEm: Float = PageGeometry.GAP_EM,
): Float {
    val gaps = (row.tokens.size - 1).coerceAtLeast(0)
    return row.tokens.sumOf { tokenWidthEm(it).toDouble() }.toFloat() + gaps * gapEm
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
    // A line that was short at the unscaled size may not fit with natural gaps once enlarged.
    val gap =
        when {
            gapCount == 0 -> 0f
            canStretch -> (frame.right - frame.left - wordsWidth) / gapCount
            else ->
                minOf(
                    fontSize * PageGeometry.GAP_EM,
                    (frame.right - frame.left - wordsWidth) / gapCount,
                )
        }
    val lineWidth = wordsWidth + gap * gapCount
    var right = if (canStretch) frame.right else (frame.left + frame.right + lineWidth) / 2f
    return row.tokens.mapIndexed { index, token ->
        val placement = TokenPlacement(token, left = right - widths[index], right = right)
        right -= widths[index] + gap
        placement
    }
}

/** Where a selection is: the rectangle of its first line and the rectangle around all of it. */
data class SelectionBounds(val firstLine: Rect, val all: Rect) {
    fun translate(offset: Offset) =
        SelectionBounds(firstLine.translate(offset), all.translate(offset))
}

/**
 * Where the ayahs of [ayahs] are: the rectangle of their first line, for the floating bar to sit
 * above, and the rectangle around all of them. Null when none is on this page.
 */
internal fun PageGeometry.selectionBounds(ayahs: Set<AyahRef>): SelectionBounds? {
    val fragments = ayahs.flatMap { ayahFragments(it) }
    if (fragments.isEmpty()) return null
    val firstTop = fragments.minOf { it.top }
    val firstLine = fragments.filter { it.top == firstTop }
    return SelectionBounds(
        firstLine =
            Rect(
                left = firstLine.minOf { it.left },
                top = firstTop,
                right = firstLine.maxOf { it.right },
                bottom = firstTop + slotHeightPx,
            ),
        all =
            Rect(
                left = fragments.minOf { it.left },
                top = firstTop,
                right = fragments.maxOf { it.right },
                bottom = fragments.maxOf { it.bottom },
            ),
    )
}
