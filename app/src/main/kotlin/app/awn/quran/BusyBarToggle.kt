package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.feature.read.quran.BusyState
import app.awn.quran.feature.recite.MicrophoneAccess
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.feature.recite.rememberMicrophonePermission
import app.awn.quran.feature.recite.stopMicrophone
import app.awn.quran.player.PlaybackState
import app.awn.quran.services.AwnServices

/**
 * What the corner button of a busy bar shows. Audio comes first: while the reciter is played the
 * microphone is off by design, and the button must control the sound. Null when nothing is busy.
 */
internal fun busyStateOf(
    playback: PlaybackState,
    isReciting: Boolean,
    phase: RecitationPhase,
): BusyState? =
    when {
        playback is PlaybackState.Playing || playback is PlaybackState.Downloading ->
            BusyState.AudioPlaying
        playback is PlaybackState.Paused -> BusyState.AudioPaused
        isReciting && phase == RecitationPhase.Listening -> BusyState.MicrophoneOn
        isReciting -> BusyState.MicrophoneOff
        else -> null
    }

@Composable
internal fun rememberBusyState(services: AwnServices): BusyState? {
    val playback by
        services.audioPlayer.state.collectAsStateWithLifecycle(initialValue = PlaybackState.Idle)
    val isReciting by services.recite.isActive.collectAsStateWithLifecycle()
    val phase by services.recite.session.phase.collectAsStateWithLifecycle()
    return busyStateOf(playback, isReciting, phase)
}

/**
 * Does what the corner button's icon says. Starting the microphone needs the permission; without
 * it, [openFullBar] shows the recite bar, which asks for it.
 */
internal fun toggleBusy(
    state: BusyState?,
    services: AwnServices,
    microphoneAccess: MicrophoneAccess,
    openFullBar: () -> Unit,
) {
    when (state) {
        BusyState.AudioPlaying -> services.audioPlayer.pause()
        BusyState.AudioPaused -> services.audioPlayer.resume()
        BusyState.MicrophoneOn -> services.recite.stopMicrophone()
        BusyState.MicrophoneOff ->
            if (microphoneAccess == MicrophoneAccess.Granted) {
                services.recite.session.start()
            } else {
                openFullBar()
            }
        null -> openFullBar()
    }
}

/** The click handler for the corner button; it reads the permission when the button is tapped. */
@Composable
internal fun rememberBusyToggle(
    services: AwnServices,
    state: BusyState?,
): (openFullBar: () -> Unit) -> Unit {
    val microphonePermission = rememberMicrophonePermission()
    return { openFullBar -> toggleBusy(state, services, microphonePermission.access, openFullBar) }
}
