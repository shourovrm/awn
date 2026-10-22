package app.awn.quran.feature.read.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PageLayout

/**
 * The sizes a page is laid out in, in pixels: the whole area of the page, the room the bars take
 * above and below, and the height of one frame row (the surah header, the page number).
 */
internal class PageArea(
    val widthPx: Float,
    val heightPx: Float,
    private val barInsets: PageBarInsets,
    private val frameRowPx: Float,
) {
    /** The height between the bars and the two frame rows, where a page that fits is placed. */
    val bodyHeightPx: Float
        get() =
            (heightPx - barInsets.topPx - barInsets.bottomPx - 2f * frameRowPx).coerceAtLeast(1f)

    /** Where the page itself starts, counted from the top of the area. */
    val pageTopPx: Float
        get() = barInsets.topPx + frameRowPx
}

/** What surrounds the page text: the header and number rows and the bars' room. */
internal class PageFrameContent(
    val layout: PageLayout,
    val chapters: List<Chapter>,
    val showPageInfo: Boolean,
    val barInsets: PageBarInsets,
)

/** A page that fits the screen: the header, the page and the number sit between the bars. */
@Composable
internal fun FittedFrame(frame: PageFrameContent, body: @Composable () -> Unit) {
    val density = LocalDensity.current
    val topPadding = with(density) { frame.barInsets.topPx.toDp() }
    val bottomPadding = with(density) { frame.barInsets.bottomPx.toDp() }
    Column(Modifier.fillMaxSize().padding(top = topPadding, bottom = bottomPadding)) {
        if (frame.showPageInfo) PageHeaderRow(frame.layout, frame.chapters)
        Box(Modifier.weight(1f).fillMaxWidth()) { body() }
        if (frame.showPageInfo) PageNumberRow(frame.layout.page)
    }
}

/**
 * A page taller than the screen: the header, the page and the number scroll together, with the room
 * of the bars as padding at both ends. The header and number scroll with the page because pinned at
 * the screen edge they would sit under the bars.
 */
@Composable
internal fun ScrollingFrame(
    frame: PageFrameContent,
    pageHeightPx: Float,
    scrollState: ScrollState,
    body: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val topPadding = with(density) { frame.barInsets.topPx.toDp() }
    val bottomPadding = with(density) { frame.barInsets.bottomPx.toDp() }
    val pageHeight = with(density) { pageHeightPx.toDp() }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        if (frame.showPageInfo) PageHeaderRow(frame.layout, frame.chapters)
        Box(Modifier.fillMaxWidth().height(pageHeight)) { body() }
        if (frame.showPageInfo) PageNumberRow(frame.layout.page)
    }
}

/** How many rows a page built ahead of the swipe adds in each frame. */
internal const val ROWS_REVEALED_PER_FRAME = 2

/**
 * The number of rows to draw. Drawing all rows of a page at once costs one long frame (about a
 * hundred words are composed and laid out), so a page that is built before the reader turns to it
 * ([revealGradually]) adds [ROWS_REVEALED_PER_FRAME] rows per frame. A page that is not built
 * ahead, or that turns into the page on screen while it is being built, shows all rows at once.
 */
@Composable
internal fun rememberRevealedRowCount(rowCount: Int, revealGradually: Boolean): Int {
    var revealed by remember(rowCount) { mutableIntStateOf(if (revealGradually) 0 else rowCount) }
    LaunchedEffect(rowCount, revealGradually) {
        if (!revealGradually) {
            revealed = rowCount
            return@LaunchedEffect
        }
        while (revealed < rowCount) {
            withFrameNanos {}
            revealed = nextRevealedRowCount(revealed, rowCount)
        }
    }
    return revealed
}

/** [current] rows plus one frame's worth, never more than [total]. */
internal fun nextRevealedRowCount(current: Int, total: Int): Int =
    minOf(total, current + ROWS_REVEALED_PER_FRAME)
