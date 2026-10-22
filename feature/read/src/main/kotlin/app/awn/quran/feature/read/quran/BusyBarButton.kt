package app.awn.quran.feature.read.quran

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
 * The corner button a busy bottom bar shrinks to. It does what its icon says: pause or play the
 * audio, stop or start the microphone. A tap on the page brings the full bar back. It floats over
 * the page and is not part of the bottom stack, so the lists do not make room for it.
 */
@Composable
internal fun BusyBarButton(
    state: BusyState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    ReadIconButton(
        iconRes = iconOf(state),
        description = stringResource(descriptionOf(state)),
        onClick = onToggle,
        modifier =
            modifier
                .shadow(AwnSpacing.small, AwnShapes.control)
                .background(field.barBackground, AwnShapes.control),
        tint = field.onBar,
    )
}

@DrawableRes
private fun iconOf(state: BusyState): Int =
    when (state) {
        BusyState.AudioPlaying -> R.drawable.ic_read_pause
        BusyState.AudioPaused -> R.drawable.ic_read_play
        BusyState.MicrophoneOn -> R.drawable.ic_read_stop
        BusyState.MicrophoneOff -> R.drawable.ic_read_recite
    }

@StringRes
private fun descriptionOf(state: BusyState): Int =
    when (state) {
        BusyState.AudioPlaying -> R.string.busy_pause
        BusyState.AudioPaused -> R.string.busy_play
        BusyState.MicrophoneOn -> R.string.busy_stop_microphone
        BusyState.MicrophoneOff -> R.string.busy_start_microphone
    }

/**
 * Keeps [content] composed but neither drawn, touched nor read aloud when [isShown] is false and
 * reports no size. The recite and Hifz bars end the recitation when they leave the composition, so
 * a bar that is only tucked away must stay in it.
 */
@Composable
internal fun TuckedAwayWhenHidden(isShown: Boolean, content: @Composable () -> Unit) {
    val tuckedAway =
        Modifier.clearAndSetSemantics {}
            .layout { measurable, constraints ->
                measurable.measure(constraints)
                layout(0, 0) {}
            }
    Box(if (isShown) Modifier else tuckedAway) { content() }
}
