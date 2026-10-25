package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import kotlin.math.abs

internal const val DISABLED_ALPHA = 0.38f

// The speeds the popup offers, one for each strictness level of the tracker (Strictness.fromSpeed):
// Lenient, Normal and Strict.
private val speedSteps = listOf(0f, 0.5f, 1f)

private val speedLabels =
    listOf(
        R.string.recite_speed_slowest,
        R.string.recite_speed_normal,
        R.string.recite_speed_fastest,
    )

@Composable
internal fun MicrophoneButton(
    listening: Boolean,
    access: MicrophoneAccess,
    enabled: Boolean,
    colors: ReciteBarColors,
    onClick: () -> Unit,
) {
    val description =
        when {
            listening -> stringResource(R.string.recite_stop)
            access == MicrophoneAccess.Granted -> stringResource(R.string.recite_start)
            access == MicrophoneAccess.CanAsk -> stringResource(R.string.recite_microphone_allow)
            else -> stringResource(R.string.recite_microphone_open_settings)
        }
    val icon = if (listening) R.drawable.ic_recite_stop else R.drawable.ic_recite_mic
    // The one primary action, in the fill the colour mode gives a button on the bar.
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.compactButton)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(AwnShapes.control)
                .background(colors.primaryFill)
                .semantics { contentDescription = description }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(RECITE_BAR_MIC_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.onPrimary)
    }
}

@Composable
internal fun SpeedButton(speed: Float, onSpeedChange: (Float) -> Unit, colors: ReciteBarColors) {
    var open by remember { mutableStateOf(false) }
    val nearestStep = speedSteps.indices.minBy { abs(speedSteps[it] - speed) }
    Box {
        BarIconButton(
            icon = R.drawable.ic_recite_speed,
            description = stringResource(R.string.recite_speed),
            onClick = { open = true },
            tag = RECITE_BAR_SPEED_TAG,
            colors = colors,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Awn.field.background,
        ) {
            // Fastest on top: the popup reads from faster down to slower as a vertical scale.
            for (step in speedSteps.indices.reversed()) {
                SpeedChoice(
                    label = stringResource(speedLabels[step]),
                    selected = step == nearestStep,
                    onClick = {
                        onSpeedChange(speedSteps[step])
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SpeedChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val field = Awn.field
    Row(
        Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(AwnSpacing.large)) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_recite_check),
                    contentDescription = null,
                    tint = field.content,
                )
            }
        }
        Text(
            text = label,
            style = AwnTypography.body,
            color = field.content,
            modifier = Modifier.padding(start = AwnSpacing.small),
        )
    }
}

@Composable
internal fun BarIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tag: String,
    colors: ReciteBarColors,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.compactButton)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { contentDescription = description }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content)
    }
}

/** A switch drawn as an icon: the icon takes the accent colour while it is on. */
@Composable
internal fun BarToggleButton(
    icon: Int,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    colors: ReciteBarColors,
) {
    val tint: Color = if (checked) colors.primaryFill else colors.content
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.compactButton)
                .semantics { contentDescription = description }
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint)
    }
}
