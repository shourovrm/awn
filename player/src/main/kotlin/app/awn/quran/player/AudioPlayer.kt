package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        /** The step of a cumulative drill, or null when the range plays in plain order. */
        val step: PlayStep? = null,
    ) : PlaybackState

    data class Paused(
        val reciterId: String,
        val ayah: AyahRef,
        val positionMs: Long,
        val step: PlayStep? = null,
    ) : PlaybackState

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

    /** [problem] is set when a download failed for a cause the screen can name with numbers. */
    data class Error(val message: String, val problem: DownloadProblem? = null) : PlaybackState
}

/**
 * The audio of a range is missing and [sizeBytes] is a lot to fetch (or the network is [metered]),
 * so the player waits for the user to choose: download it, or stream it. Answer with
 * [AudioPlayer.answerDownloadPrompt]. While it shows, [AudioPlayer.state] stays idle.
 */
data class DownloadPrompt(val sizeBytes: Long, val metered: Boolean)

/** How long the learner gets to repeat an ayah before the next one starts. */
@Suppress("MagicNumber") // The factors are what the names say.
enum class PauseAfterAyah(val lengthFactor: Double) {
    Off(0.0),

    /** As long as the ayah took to play. */
    Once(1.0),
    OneAndHalf(1.5),
    Twice(2.0),
}

/**
 * How a range is played. [repeatEachAyah] and [repeatRange] are the total number of plays (1 plays
 * once), or [INFINITE_REPEAT]. [speed] is a multiplier from [MIN_SPEED] to [MAX_SPEED].
 * [pauseAfterAyah] adds silence after each ayah for the learner to repeat it. [cumulative] plays 1,
 * 2, 1+2, 3, 1+2+3 and so on; it is read when a range starts, the other options at once.
 * [stopOnTaskRemoved] stops playback when the app is swiped away from the recent apps.
 */
data class PlaybackOptions(
    val repeatEachAyah: Int = 1,
    val repeatRange: Int = 1,
    val speed: Float = 1f,
    val pauseAfterAyah: PauseAfterAyah = PauseAfterAyah.Off,
    val cumulative: Boolean = false,
    val stopOnTaskRemoved: Boolean = true,
) {
    /** The same options with every value forced into its allowed range. */
    fun coerced(): PlaybackOptions =
        copy(
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

/** When playback stops by itself: after the ayah now playing, at the end of its surah, or later. */
sealed interface SleepTimer {
    data object EndOfAyah : SleepTimer

    data object EndOfSurah : SleepTimer

    /** Stops [minutes] minutes after it is set. */
    data class Minutes(val minutes: Int) : SleepTimer
}

@Suppress("TooManyFunctions") // One function per control of the player.
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

    /**
     * Drops a [play] request that has not started to sound, one still downloading or waiting for
     * the user's answer. Audio that is playing or paused is left as it is.
     */
    fun cancelPending() = Unit

    /** Skips to the next ayah of the range; after the last ayah the range ends as usual. */
    fun next()

    /**
     * Goes back to the start of the current ayah when it has played for more than three seconds,
     * otherwise to the previous ayah of the range.
     */
    fun previous()

    /** Replaces the repeat and speed settings; takes effect at once. */
    fun setOptions(options: PlaybackOptions)

    /**
     * Gets ready to play [reciterId] before the user presses play: connects the media session and
     * loads the reciter and surah lists, so the first play reaches the speaker sooner. Safe to call
     * again; does nothing audible.
     */
    fun warmUp(reciterId: String) = Unit

    /** The question being asked before a big download, or null. */
    val downloadPrompt: StateFlow<DownloadPrompt?>
        get() = NoDownloadPrompt

    /**
     * Answers the [downloadPrompt]: true downloads the audio, then plays; false plays by streaming.
     * Does nothing when no prompt is showing.
     */
    fun answerDownloadPrompt(download: Boolean) = Unit

    /** The sleep timer that is running, or null. It is cleared when it fires or playback stops. */
    val sleepTimer: StateFlow<SleepTimer?>

    /** Starts [timer], replacing the running one; null cancels it. */
    fun setSleepTimer(timer: SleepTimer?)

    /** Repeats each ayah (or the single word) [times] more times; 0 turns repeating off. */
    fun setRepeat(times: Int) =
        setOptions(options.value.copy(repeatEachAyah = times.coerceAtLeast(0) + 1))
}

private val NoDownloadPrompt: StateFlow<DownloadPrompt?> = MutableStateFlow(null)
