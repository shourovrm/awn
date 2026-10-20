package app.awn.quran.feature.read

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing

private const val DISABLED_ALPHA = 0.38f

/**
 * A 48 dp icon button. [description] is read by screen readers and is required because the icon is
 * the only label; pressing and holding the button also shows it as a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadIconButton(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    tint: Color = Awn.field.buttonFill,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(AwnSpacing.touchTarget),
            enabled = isEnabled,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = if (isEnabled) tint else tint.copy(alpha = DISABLED_ALPHA),
            )
        }
    }
}
