package app.awn.quran.services

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays one word and returns when it has finished. The player returns as soon as playback starts,
 * so without the wait a list of words would cut each other off and a lesson could not mark the word
 * that is sounding.
 */
internal suspend fun AudioPlayer.playWordToEnd(reciterId: String, ayah: AyahRef, position: Int) {
    playWord(reciterId, ayah, position)
    // The first play of a reciter may download its audio before it sounds.
    val started =
        withTimeoutOrNull(START_TIMEOUT_MILLIS) { state.first { it is PlaybackState.Playing } }
    if (started != null) {
        withTimeoutOrNull(WORD_TIMEOUT_MILLIS) { state.first { it !is PlaybackState.Playing } }
    }
}

private const val START_TIMEOUT_MILLIS = 15_000L

// A single word is a few seconds long; the limit only guards against a player that never stops.
private const val WORD_TIMEOUT_MILLIS = 20_000L
