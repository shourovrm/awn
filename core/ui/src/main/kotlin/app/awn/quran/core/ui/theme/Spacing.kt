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
    val settingsRowHeight: Dp = 44.dp

    val switchWidth: Dp = 48.dp
    val switchHeight: Dp = 28.dp
    val switchKnobSize: Dp = 24.dp
    val switchPadding: Dp = 2.dp

    val dockHeight: Dp = 72.dp
    val dockFloatGap: Dp = 12.dp

    val sliderTrackHeight: Dp = 8.dp
    val sliderThumbSize: Dp = 24.dp

    val controlCorner: Dp = 14.dp
    val wordBlockCorner: Dp = 8.dp
    val sliderThumbCorner: Dp = 7.dp
    val switchKnobCorner: Dp = 4.dp
}

object AwnShapes {
    val control = RoundedCornerShape(AwnSpacing.controlCorner)
    val wordBlock = RoundedCornerShape(AwnSpacing.wordBlockCorner)
    val switchTrack = RoundedCornerShape(AwnSpacing.wordBlockCorner)
    val switchKnob = RoundedCornerShape(AwnSpacing.switchKnobCorner)
}
