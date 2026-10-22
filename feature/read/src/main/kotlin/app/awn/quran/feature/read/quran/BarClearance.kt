package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.read.page.PageBarInsets

/**
 * How much room the Mushaf page keeps free under the bars that float over it. The bars report the
 * height they were laid out with, and the clearance keeps the tallest height seen, so a bar that
 * grows (a top bar on two rows at a large font, a recite bar of three rows) moves the page once and
 * a bar that hides or shrinks does not move it back. Until a bar has been measured the clearance is
 * a floor: the height of the top bar under the status bar, and of the audio bar's dock.
 *
 * The top and the audio bar are measured while they show, which they do for two seconds when the
 * screen opens. The recite and Hifz bars have their own height while reciting and are forgotten
 * when reciting ends ([onReciteEnded]), so the page does not keep a gap for a bar that is gone.
 */
@Stable
internal class BarClearance(topFloorPx: Int, bottomFloorPx: Int) {
    var topPx by mutableIntStateOf(topFloorPx)
        private set

    private var audioBarPx by mutableIntStateOf(bottomFloorPx)

    /** The height of everything stacked at the bottom now while reciting; 0 otherwise. */
    var reciteBarPx by mutableIntStateOf(0)
        private set

    /** The tallest that stack was in this reciting session: bars, pill and mistake card. */
    private var tallestReciteBarPx by mutableIntStateOf(0)

    fun onTopBarMeasured(heightPx: Int) {
        topPx = maxOf(topPx, heightPx)
    }

    fun onBottomBarMeasured(heightPx: Int, isReciting: Boolean) {
        if (isReciting) {
            reciteBarPx = heightPx
            tallestReciteBarPx = maxOf(tallestReciteBarPx, heightPx)
        } else {
            audioBarPx = maxOf(audioBarPx, heightPx)
        }
    }

    fun onReciteEnded() {
        reciteBarPx = 0
        tallestReciteBarPx = 0
    }

    /** The room under the page: the tallest bar that can show in the current mode. */
    fun bottomPx(isReciting: Boolean): Int =
        if (isReciting) maxOf(audioBarPx, tallestReciteBarPx) else audioBarPx

    fun insets(isReciting: Boolean): PageBarInsets = PageBarInsets(topPx, bottomPx(isReciting))
}

/** The [BarClearance] of the screen, starting from the heights the bars are designed with. */
@Composable
internal fun rememberBarClearance(): BarClearance {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val topFloorPx = statusBarPx + with(density) { AwnSpacing.topBarHeight.roundToPx() }
    val bottomFloorPx = with(density) { AwnSpacing.dockHeight.roundToPx() }
    return remember(topFloorPx, bottomFloorPx) { BarClearance(topFloorPx, bottomFloorPx) }
}
