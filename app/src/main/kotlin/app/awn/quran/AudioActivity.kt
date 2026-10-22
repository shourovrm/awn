package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.player.PlaybackState
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Whether audio plays, is paused or is being fetched, for the Quran screen's bottom bar. */
@Composable
internal fun rememberIsAudioActive(services: AwnServices): Boolean {
    val isActive by
        remember(services) { services.audio.player.state.map(::isAudioBusy).distinctUntilChanged() }
            .collectAsStateWithLifecycle(initialValue = false)
    return isActive
}

/** True while audio plays, is paused or is being fetched: the bottom bar then has work to show. */
private fun isAudioBusy(playback: PlaybackState): Boolean =
    when (playback) {
        is PlaybackState.Playing,
        is PlaybackState.Paused,
        is PlaybackState.Downloading -> true
        is PlaybackState.Idle,
        is PlaybackState.Error -> false
    }
