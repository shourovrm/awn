package app.awn.quran.feature.read.quran

import kotlin.math.abs

/**
 * How many pages on either side of the page on screen keep their loaded text in memory. The pager
 * shows one page on either side, and a few more cover a quick swipe back; every other page is
 * loaded again from the database when the reader comes back to it.
 */
internal const val LOADED_PAGE_RADIUS = 4

/** The entries of [loaded] whose page is within [radius] pages of [centerPage]. */
internal fun <T> keepPagesNear(
    loaded: Map<Int, T>,
    centerPage: Int,
    radius: Int = LOADED_PAGE_RADIUS,
): Map<Int, T> = loaded.filterKeys { page -> abs(page - centerPage) <= radius }
