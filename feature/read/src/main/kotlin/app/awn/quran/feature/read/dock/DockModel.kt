package app.awn.quran.feature.read.dock

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackState

/** What the collapsed dock shows for a loaded audio. */
data class DockModel(val reciterId: String, val ayah: AyahRef, val isPlaying: Boolean)

/** Returns the dock content for [state], or null when no audio is loaded (nothing to show). */
fun dockModelOf(state: PlaybackState): DockModel? =
    when (state) {
        is PlaybackState.Playing -> DockModel(state.reciterId, state.ayah, isPlaying = true)
        is PlaybackState.Paused -> DockModel(state.reciterId, state.ayah, isPlaying = false)
        is PlaybackState.Idle,
        is PlaybackState.Error -> null
    }
