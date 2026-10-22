package app.awn.quran.feature.read.page

/**
 * A page placed at a chosen size: the [rows] it is drawn with (the printed lines, or new lines when
 * the size made a printed line too wide), where everything sits, and the height of the whole page.
 */
internal class PagePlan(
    val rows: List<PageRow>,
    val geometry: PageGeometry,
    val pageHeightPx: Float,
)

/**
 * Places a page at [sizeFactor] times its default size, where the default is the size the page has
 * at factor 1: the printed lines fill the width in portrait, and in landscape the page is as wide
 * as the screen and taller than its body ([bodyHeightPx]).
 *
 * The font and the line spacing both scale with [sizeFactor], so a smaller page is the same print
 * in a narrower column, centred. While every printed line still fits the width, the lines stay as
 * printed. When a bigger font makes one too wide, the words of the page are poured into new lines
 * that fit ([breakRowsToWidth]) and the page grows taller than the body; the caller then scrolls
 * it. A page shorter than the body sits in the vertical middle of it.
 */
internal fun planPage(
    rows: List<PageRow>,
    size: PagePlacement,
    tokenWidthEm: (LineToken) -> Float,
): PagePlan {
    val defaultHeightPx = pageHeightFor(size.widthPx, size.bodyHeightPx)
    val defaultGeometry =
        layoutPage(
            rows,
            size.isCentredBlock,
            size.widthPx,
            defaultHeightPx,
            tokenWidthEm,
            size.slotCount,
        )
    val availableWidth = availableWidthOf(size.widthPx, size.isCentredBlock)

    val fontSizePx =
        minOf(
            defaultGeometry.fontSizePx * size.sizeFactor,
            availableWidth / widestTokenEm(rows, tokenWidthEm),
        )
    val sizeRatio = fontSizePx / defaultGeometry.fontSizePx
    val slotHeightPx = defaultGeometry.slotHeightPx * sizeRatio
    val plannedRows = breakRowsToWidth(rows, tokenWidthEm, availableWidth / fontSizePx)

    val pageHeightPx =
        maxOf(
            defaultHeightPx * sizeRatio,
            heightNeededFor(plannedRows, slotHeightPx, size.isCentredBlock),
        )
    val centringOffset = maxOf(0f, (size.bodyHeightPx - pageHeightPx) / 2f)
    val grid = gridFor(pageHeightPx, slotHeightPx, size.isCentredBlock, centringOffset)
    val geometry =
        placeLines(plannedRows, size.isCentredBlock, size.widthPx, grid, fontSizePx, tokenWidthEm)
    return PagePlan(plannedRows, geometry, pageHeightPx)
}

/** The widest word or end sign of the text rows in ems; 1 when the page has none. */
private fun widestTokenEm(rows: List<PageRow>, tokenWidthEm: (LineToken) -> Float): Float =
    rows
        .filterIsInstance<PageRow.TextRow>()
        .flatMap { it.tokens }
        .maxOfOrNull(tokenWidthEm)
        ?.coerceAtLeast(MIN_TOKEN_EM) ?: 1f

private const val MIN_TOKEN_EM = 0.01f

/** The grid of a page [pageHeightPx] tall, moved down by [offsetPx] to sit in the middle. */
private fun gridFor(
    pageHeightPx: Float,
    slotHeightPx: Float,
    isCentredBlock: Boolean,
    offsetPx: Float,
): SlotGrid {
    val firstSlotTop =
        if (isCentredBlock) {
            pageHeightPx * PageGeometry.CENTRED_BANNER_CENTER - slotHeightPx / 2f
        } else {
            0f
        }
    return SlotGrid(slotHeightPx, firstSlotTop + offsetPx)
}

/**
 * The height at which the last row of [rows] still lies inside the page. For pages 1 and 2 the
 * banner sits at 27 % of the height, so the height is solved from that position.
 */
private fun heightNeededFor(
    rows: List<PageRow>,
    slotHeightPx: Float,
    isCentredBlock: Boolean,
): Float {
    val lastSlot = rows.maxOfOrNull { it.slot } ?: 0
    return if (isCentredBlock) {
        (lastSlot * slotHeightPx - slotHeightPx / 2f) / (1f - PageGeometry.CENTRED_BANNER_CENTER)
    } else {
        lastSlot * slotHeightPx
    }
}
