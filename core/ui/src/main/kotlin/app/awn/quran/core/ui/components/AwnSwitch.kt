package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing

private const val OFF_TRACK_ALPHA = 0.3f

/** A 48 by 28 dp soft rectangle with a square knob, inside a 48 dp touch target. */
@Composable
fun AwnSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val trackColor = if (checked) field.buttonFill else field.content.copy(alpha = OFF_TRACK_ALPHA)
    val knobColor = if (checked) field.onButton else field.content
    val knobTravel =
        AwnSpacing.switchWidth - AwnSpacing.switchKnobSize - AwnSpacing.switchPadding * 2

    Box(
        modifier =
            modifier
                .requiredSize(AwnSpacing.touchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(AwnSpacing.switchWidth, AwnSpacing.switchHeight)
                    .clip(AwnShapes.switchTrack)
                    .background(trackColor)
                    .padding(AwnSpacing.switchPadding),
        ) {
            Box(
                modifier =
                    Modifier.offset(x = if (checked) knobTravel else 0.dp)
                        .size(AwnSpacing.switchKnobSize)
                        .clip(AwnShapes.switchKnob)
                        .background(knobColor),
            )
        }
    }
}
