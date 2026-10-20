package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.Flow

sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** [wordPosition] is null when the reciter has no word timings for the ayah. */
    data class Playing(
        val reciterId: String,
        val ayah: AyahRef,
        val wordPosition: Int?,
        val positionMs: Long,
        val durationMs: Long?,
    ) : PlaybackState

    data class Paused(val reciterId: String, val ayah: AyahRef, val positionMs: Long) :
        PlaybackState

    data class Error(val message: String) : PlaybackState
}

interface AudioPlayer {

    /** Emits the current state, then every change. Starts as [PlaybackState.Idle]. */
    val state: Flow<PlaybackState>

    /**
     * Plays from [from] up to and including [to]; [to] = null plays to the end of the surah of
     * [from]. Moves to [PlaybackState.Error] when the audio cannot be found.
     */
    suspend fun play(reciterId: String, from: AyahRef, to: AyahRef?)

    /**
     * Plays only the word at [position] (1-based) of [ayah]; needs word timings for the reciter.
     */
    suspend fun playWord(reciterId: String, ayah: AyahRef, position: Int)

    fun pause()

    fun resume()

    fun stop()

    /** Repeats the current range [times] more times; 0 turns repeating off. */
    fun setRepeat(times: Int)
}
