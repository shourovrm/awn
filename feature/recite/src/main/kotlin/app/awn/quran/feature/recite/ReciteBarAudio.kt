package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

/** Listen, which becomes Pause while the ayah plays and Resume while it is paused. */
@Composable
internal fun ListenButton(
    audio: ReciteAudioState,
    enabled: Boolean,
    colors: ReciteBarColors,
    onClick: () -> Unit,
) {
    val icon =
        when (audio) {
            ReciteAudioState.Playing -> R.drawable.ic_recite_pause
            ReciteAudioState.Paused -> R.drawable.ic_recite_play
            ReciteAudioState.Silent,
            ReciteAudioState.Loading -> R.drawable.ic_recite_listen
        }
    val description =
        when (audio) {
            ReciteAudioState.Playing -> stringResource(R.string.recite_listen_pause)
            ReciteAudioState.Paused -> stringResource(R.string.recite_listen_resume)
            ReciteAudioState.Silent,
            ReciteAudioState.Loading -> stringResource(R.string.recite_listen_ayah)
        }
    BarIconButton(
        icon = icon,
        description = description,
        onClick = onClick,
        tag = RECITE_BAR_LISTEN_TAG,
        colors = colors,
        // While the audio loads only Stop is useful.
        enabled = enabled && audio != ReciteAudioState.Loading,
    )
}
