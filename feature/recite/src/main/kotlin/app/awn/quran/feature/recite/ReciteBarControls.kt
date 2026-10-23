package app.awn.quran.feature.recite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.player.PlaybackState

/**
 * The one row of the recite bar: close, speed, hide words,
 * [while words are hidden: show next word, show ayah], listen and the microphone. Seven buttons of
 * 44 dp fill a 360 dp phone, so nothing else may join the row; messages go to the strip above the
 * bar.
 */
@Composable
internal fun ReciteBarControls(
    controller: ReciteController,
    colors: ReciteBarColors,
    permission: MicrophonePermissionState,
    canListen: Boolean,
    onClose: () -> Unit,
) {
    val session = controller.session
    val speed by session.speed.collectAsState()
    val hideWords by session.hifzEnabled.collectAsState()
    val phase by session.phase.collectAsState()
    val playback by controller.audioPlayer.state.collectAsState(initial = PlaybackState.Idle)
    val audio = reciteAudioStateOf(playback)
    val isAudioActive = audio.isActive()
    // Whatever starts the audio (Listen, the ayah bar, a word), the microphone must not hear it.
    LaunchedEffect(isAudioActive) { if (isAudioActive) controller.pauseMicrophone() }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIconButton(
            icon = R.drawable.ic_recite_close,
            description = stringResource(R.string.recite_close),
            onClick = onClose,
            tag = RECITE_BAR_CLOSE_TAG,
            colors = colors,
        )
        SpeedButton(speed = speed, onSpeedChange = controller::setSpeed, colors = colors)
        BarToggleButton(
            icon = R.drawable.ic_recite_hide_words,
            description = stringResource(R.string.recite_hide_words),
            checked = hideWords,
            onCheckedChange = controller::setHideWords,
            tag = RECITE_BAR_HIDE_TAG,
            colors = colors,
        )
        if (hideWords) {
            ReciteRevealButtons(
                controller,
                wordTag = RECITE_BAR_REVEAL_WORD_TAG,
                ayahTag = RECITE_BAR_REVEAL_AYAH_TAG,
            ) { icon, description, onClick, tag ->
                BarIconButton(icon, description, onClick, tag, colors)
            }
        }
        ListenButton(
            audio = audio,
            enabled = canListen || isAudioActive,
            colors = colors,
            onClick = { listen(controller, audio) },
        )
        MicrophoneButton(
            listening = phase == RecitationPhase.Listening,
            access = permission.access,
            // While the audio plays the microphone stops it and goes on reciting.
            enabled = canListen || phase == RecitationPhase.Listening,
            colors = colors,
            onClick = { toggleMicrophone(controller, permission, phase, isAudioActive) },
        )
    }
}

private fun listen(controller: ReciteController, audio: ReciteAudioState) {
    when (listenActionFor(audio)) {
        ListenAction.PlayAyah -> controller.listenOnward()
        ListenAction.Pause -> controller.audioPlayer.pause()
        ListenAction.Resume -> controller.audioPlayer.resume()
        null -> Unit
    }
}

private fun toggleMicrophone(
    controller: ReciteController,
    permission: MicrophonePermissionState,
    phase: RecitationPhase,
    isAudioActive: Boolean,
) {
    val isGranted = permission.access == MicrophoneAccess.Granted
    when {
        phase == RecitationPhase.Listening -> controller.stopMicrophone()
        isGranted && isAudioActive -> controller.resumeReciting()
        isGranted -> controller.session.start()
        permission.access == MicrophoneAccess.CanAsk -> permission.requestAccess()
        else -> permission.openAppSettings()
    }
}
