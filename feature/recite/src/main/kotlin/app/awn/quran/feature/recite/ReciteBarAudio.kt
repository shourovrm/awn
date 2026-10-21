package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.player.PlaybackState

private const val TRACK_ALPHA = 0.24f

/** The status line while audio is not silent: loading, paused, or which ayah plays. */
@Composable
internal fun audioStatusText(audio: ReciteAudioState, playback: PlaybackState): String =
    when {
        audio == ReciteAudioState.Loading -> stringResource(R.string.recite_status_audio_loading)
        playback is PlaybackState.Playing ->
            stringResource(R.string.recite_status_audio_playing, playback.ayah.ayah)
        else -> stringResource(R.string.recite_status_audio_paused)
    }

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

/** A thin line under the status text that fills as the audio plays. */
@Composable
internal fun AudioProgressLine(playback: PlaybackState, colors: ReciteBarColors) {
    val progress = audioProgressOf(playback)
    // A paused player reports no duration; the line keeps where it stopped.
    var shown by remember { mutableFloatStateOf(0f) }
    if (progress != null) shown = progress
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = AwnSpacing.small)
            .height(2.dp)
            .background(colors.content.copy(alpha = TRACK_ALPHA))
            .testTag(RECITE_BAR_AUDIO_PROGRESS_TAG),
    ) {
        Box(Modifier.fillMaxWidth(shown).height(2.dp).background(colors.content))
    }
}
