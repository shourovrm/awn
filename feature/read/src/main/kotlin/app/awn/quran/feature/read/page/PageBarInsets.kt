package app.awn.quran.feature.read.page

import androidx.compose.runtime.Immutable

/**
 * The height of the bars that float over the top and the bottom of the screen, in pixels. No text
 * of the page is drawn inside these strips: a page that fits the screen is placed between them, and
 * a page that scrolls starts and ends with them as padding.
 */
@Immutable data class PageBarInsets(val topPx: Int = 0, val bottomPx: Int = 0)
