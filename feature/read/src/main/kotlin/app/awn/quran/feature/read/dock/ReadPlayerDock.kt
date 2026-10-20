package app.awn.quran.feature.read.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.ui.components.AwnPlayerDock
import app.awn.quran.feature.read.R
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PlaybackState

/**
 * The collapsed player at the bottom of both reading views. It is driven by [AudioPlayer.state];
 * [onExpand] opens the full player, which :app provides. [reciterName] turns a reciter id into the
 * name shown.
 */
@Composable
fun ReadPlayerDock(
    audioPlayer: AudioPlayer,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    reciterName: (String) -> String = { it },
) {
    val state by audioPlayer.state.collectAsStateWithLifecycle(initialValue = PlaybackState.Idle)
    val model = dockModelOf(state) ?: return
    AwnPlayerDock(
        title = stringResource(R.string.dock_title, reciterName(model.reciterId), model.ayah.ayah),
        isPlaying = model.isPlaying,
        onPlayPauseClick = { if (model.isPlaying) audioPlayer.pause() else audioPlayer.resume() },
        onExpandClick = onExpand,
        playLabel = stringResource(R.string.dock_play),
        pauseLabel = stringResource(R.string.dock_pause),
        expandLabel = stringResource(R.string.dock_expand),
        modifier = modifier,
    )
}
