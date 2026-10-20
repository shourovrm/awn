package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * The audio of the requested range is being downloaded before it plays. [totalBytes] is an
     * estimate until the last file starts, and null until the first file's size is known.
     */
    data class Downloading(val doneBytes: Long, val totalBytes: Long?) : PlaybackState {
        /** Fraction done from 0 to 1, or null while the total is unknown. */
        val fraction: Float?
            get() =
                totalBytes?.takeIf { it > 0L }?.let { (doneBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    data class Error(val message: String) : PlaybackState
}

/**
 * How a range is played. [repeatEachAyah] and [repeatRange] are the total number of plays (1 plays
 * once), or [INFINITE_REPEAT]. [speed] is a multiplier from [MIN_SPEED] to [MAX_SPEED].
 */
data class PlaybackOptions(
    val repeatEachAyah: Int = 1,
    val repeatRange: Int = 1,
    val speed: Float = 1f,
) {
    /** The same options with every value forced into its allowed range. */
    fun coerced(): PlaybackOptions =
        PlaybackOptions(
            repeatEachAyah = repeatEachAyah.coerceAtLeast(1),
            repeatRange = repeatRange.coerceAtLeast(1),
            speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        )

    companion object {
        /** Stands for "repeat without end" in [repeatEachAyah] and [repeatRange]. */
        const val INFINITE_REPEAT = Int.MAX_VALUE
        const val MIN_SPEED = 0.75f
        const val MAX_SPEED = 1.5f
    }
}

interface AudioPlayer {

    /** Emits the current state, then every change. Starts as [PlaybackState.Idle]. */
    val state: Flow<PlaybackState>

    /** The repeat and speed settings used by the range playing now and by the next one. */
    val options: StateFlow<PlaybackOptions>

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

    /** Stops playback, and cancels a download that [PlaybackState.Downloading] is showing. */
    fun stop()

    /** Skips to the next ayah of the range; after the last ayah the range ends as usual. */
    fun next()

    /**
     * Goes back to the start of the current ayah when it has played for more than three seconds,
     * otherwise to the previous ayah of the range.
     */
    fun previous()

    /** Replaces the repeat and speed settings; takes effect at once. */
    fun setOptions(options: PlaybackOptions)

    /** Repeats each ayah (or the single word) [times] more times; 0 turns repeating off. */
    fun setRepeat(times: Int) =
        setOptions(options.value.copy(repeatEachAyah = times.coerceAtLeast(0) + 1))
}
