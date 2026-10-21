package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.raisedSurface
import app.awn.quran.core.ui.theme.scrim

/**
 * A card centred over the dimmed page, used by the print offer and Go to page. Tapping the dim area
 * calls [onDismiss]; the card itself swallows taps so they do not reach the dim area. The card
 * rises above the keyboard so a focused field stays visible.
 */
@Composable
internal fun FloatingCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val field = Awn.field
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(field.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .imePadding()
                .padding(horizontal = AwnSpacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.widthIn(max = AwnSpacing.cardMaxWidth)
                    .fillMaxWidth()
                    .shadow(AwnSpacing.cardElevation, AwnShapes.control)
                    .clip(AwnShapes.control)
                    .background(field.raisedSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(
                        PaddingValues(
                            start = AwnSpacing.cardInnerPadding,
                            end = AwnSpacing.cardInnerPadding,
                            top = AwnSpacing.large,
                            bottom = AwnSpacing.small,
                        ),
                    ),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            content = content,
        )
    }
}

/** A borderless 48dp text action in the field's button colour, such as Not now. */
@Composable
internal fun CardTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = Awn.field.buttonFill)
    }
}
