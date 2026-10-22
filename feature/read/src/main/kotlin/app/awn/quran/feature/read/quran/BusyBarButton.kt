package app.awn.quran.feature.read.quran

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton

/**
 * The corner button a busy bottom bar shrinks to: a pause icon while audio plays, a microphone
 * while reciting. A tap brings the full bar back. It floats over the page and is not part of the
 * bottom stack, so the lists do not make room for it.
 */
@Composable
internal fun BusyBarButton(isReciting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val field = Awn.field
    @DrawableRes val icon = if (isReciting) R.drawable.ic_read_recite else R.drawable.ic_read_pause
    ReadIconButton(
        iconRes = icon,
        description = stringResource(R.string.action_show_controls),
        onClick = onClick,
        modifier =
            modifier
                .shadow(AwnSpacing.small, AwnShapes.control)
                .background(field.barBackground, AwnShapes.control),
        tint = field.onBar,
    )
}

/**
 * Keeps [content] composed but neither drawn, touched nor read aloud when [isShown] is false and
 * reports no size. The recite and Hifz bars end the recitation when they leave the composition, so
 * a bar that is only tucked away must stay in it.
 */
@Composable
internal fun TuckedAwayWhenHidden(isShown: Boolean, content: @Composable () -> Unit) {
    val tuckedAway =
        Modifier.clearAndSetSemantics {}.layout { measurable, constraints ->
            measurable.measure(constraints)
            layout(0, 0) {}
        }
    Box(if (isShown) Modifier else tuckedAway) { content() }
}
