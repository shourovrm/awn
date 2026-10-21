package app.awn.quran.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing

private const val OFF_RAIL_ALPHA = 0.25f
private const val ON_RAIL_ALPHA = 0.45f
private const val OFF_RING_ALPHA = 0.55f
private const val SLIDE_MILLISECONDS = 120

/**
 * A thin-rail switch: a 32 dp wide, 4 dp tall rail and a 16 dp round knob, inside a 48 dp touch
 * target. Off, the rail is the ink at 25 % and the knob is the page colour with a 2 dp ring of the
 * ink at 55 %; on, the rail is the accent at 45 % and the knob is the accent. The knob slides in
 * 120 ms.
 */
@Composable
fun AwnSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchGraphic(
        checked = checked,
        modifier =
            modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

/**
 * The drawing of the switch without any touch handling, for a row that toggles as a whole. It is
 * still 48 dp square so that the row keeps the same height with or without its own handling.
 */
@Composable
internal fun SwitchGraphic(checked: Boolean, modifier: Modifier = Modifier) {
    val field = Awn.field
    val accent = field.buttonFill
    val railColor =
        if (checked) accent.copy(alpha = ON_RAIL_ALPHA)
        else field.content.copy(alpha = OFF_RAIL_ALPHA)
    val knobTravel = AwnSpacing.switchWidth - AwnSpacing.switchKnobSize
    val knobOffset by
        animateDpAsState(
            targetValue = if (checked) knobTravel else 0.dp,
            animationSpec = tween(SLIDE_MILLISECONDS),
        )
    // The ring is drawn inside the knob, so an off knob keeps the same 16 dp as an on knob.
    val knobLook =
        if (checked) {
            Modifier.background(accent)
        } else {
            Modifier.background(field.background)
                .border(
                    AwnSpacing.switchKnobRing,
                    field.content.copy(alpha = OFF_RING_ALPHA),
                    CircleShape,
                )
        }

    Box(
        modifier = modifier.requiredSize(AwnSpacing.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(AwnSpacing.switchWidth, AwnSpacing.switchKnobSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(AwnSpacing.switchWidth, AwnSpacing.switchRailHeight)
                    .clip(CircleShape)
                    .background(railColor),
            )
            Box(
                Modifier.offset { IntOffset(knobOffset.roundToPx(), 0) }
                    .size(AwnSpacing.switchKnobSize)
                    .clip(CircleShape)
                    .then(knobLook),
            )
        }
    }
}
