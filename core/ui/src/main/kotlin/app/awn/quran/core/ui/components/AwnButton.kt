package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

private const val DISABLED_ALPHA = 0.4f

/** A flat 48 dp button. Use one per screen for the primary action. */
@Composable
fun AwnButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val field = Awn.field
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = AwnSpacing.buttonHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(AwnShapes.control)
                .background(field.buttonFill)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = field.onButton)
    }
}
