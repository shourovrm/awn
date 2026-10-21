package app.awn.quran.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every length in the app comes from here; the 8 dp grid is in docs/design-system.md. */
object AwnSpacing {
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp
    val extraLarge: Dp = 32.dp
    val huge: Dp = 48.dp
    val giant: Dp = 64.dp

    val screenMargin: Dp = medium
    val sectionGap: Dp = extraLarge

    val touchTarget: Dp = 48.dp
    val buttonHeight: Dp = 48.dp
    val posterBandHeight: Dp = 80.dp
    val settingsRowHeight: Dp = 48.dp
    val topBarHeight: Dp = 56.dp
    val iconSize: Dp = 24.dp
    val tabIndicatorHeight: Dp = 4.dp
    val dialogMaxWidth: Dp = 320.dp
    val cardElevation: Dp = 12.dp
    val cardMaxWidth: Dp = 400.dp
    val cardInnerPadding: Dp = 20.dp

    // Thin-rail switch: a 4 dp rail and a 16 dp knob in a 32 dp wide frame; the 48 dp touch
    // target around it is touchTarget.
    val switchWidth: Dp = 32.dp
    val switchRailHeight: Dp = 4.dp
    val switchKnobSize: Dp = 16.dp
    val switchKnobRing: Dp = 2.dp

    // Settings rows: two-line rows are 64 dp, one-line rows 52 dp, as in the quiet-list mockup.
    val settingsRowTall: Dp = 64.dp
    val settingsRowShort: Dp = 52.dp
    val searchFieldHeight: Dp = 44.dp
    val menuItemHeight: Dp = 44.dp
    val menuMinWidth: Dp = 168.dp
    val stepperValueWidth: Dp = 56.dp
    val tickBoxSize: Dp = 22.dp
    val tickBoxCorner: Dp = 6.dp
    val packProgressHeight: Dp = 3.dp
    val groupHeadingTop: Dp = 24.dp
    val smallIconSize: Dp = 20.dp
    val previewHeight: Dp = 240.dp

    // First-launch language screen: the app mark, the two large language blocks and their mark.
    val welcomeTopPadding: Dp = 32.dp
    val welcomeLogoSize: Dp = 64.dp
    val languageBlockHeight: Dp = 72.dp
    val languageBlockMarkSize: Dp = 24.dp

    val dockHeight: Dp = 72.dp
    val dockFloatGap: Dp = 12.dp

    // Quran tab: the number badge of a list row, the juz rail, the tab bar and its indicator.
    val numberBadge: Dp = 40.dp
    val railWidth: Dp = 32.dp
    val railMarkerSize: Dp = 24.dp
    val tabBarHeight: Dp = 64.dp
    val tabIndicatorWidth: Dp = 32.dp
    val cardRowHeight: Dp = 56.dp

    val sliderTrackHeight: Dp = 8.dp
    val sliderThumbSize: Dp = 24.dp

    val controlCorner: Dp = 14.dp
    val wordBlockCorner: Dp = 8.dp
    val sliderThumbCorner: Dp = 7.dp
    val switchKnobCorner: Dp = 4.dp
    val menuCorner: Dp = 12.dp
    val searchFieldCorner: Dp = 12.dp
}

object AwnShapes {
    val control = RoundedCornerShape(AwnSpacing.controlCorner)
    val wordBlock = RoundedCornerShape(AwnSpacing.wordBlockCorner)
    val menu = RoundedCornerShape(AwnSpacing.menuCorner)
    val searchField = RoundedCornerShape(AwnSpacing.searchFieldCorner)
    val tickBox = RoundedCornerShape(AwnSpacing.tickBoxCorner)
}
