package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PlaybackState
import app.awn.quran.player.PlaylistBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What [ListenPanel] shows. [isVisible] is false while nothing is loaded. */
data class ListenPanelState(
    val isVisible: Boolean = false,
    val reciterName: String = "",
    val isPlaying: Boolean = false,
    val ayahNumber: Int = 0,
    val ayahCount: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val repeatTimes: Int = 0,
    val hasError: Boolean = false,
) {
    /** Fraction of the current ayah played, 0 to 1. */
    val progress: Float
        get() {
            val total = durationMs ?: return 0f
            return if (total > 0L) (positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
}

/**
 * Drives the player panel: starts a range with the selected reciter, steps to the previous or next
 * ayah, pauses and resumes, and cycles the repeat count.
 */
class ListenViewModel(
    private val player: AudioPlayer,
    private val reciters: ReciterRepository,
    quranText: QuranTextRepository,
    private val settings: ListenSettings,
) : ViewModel() {

    private class LoadedRange(val reciterId: String, val ayahs: List<AyahRef>)

    private val playlistBuilder = PlaylistBuilder(quranText)
    private val loadedRange = MutableStateFlow<LoadedRange?>(null)
    private val repeatTimes = MutableStateFlow(0)
    private val reciterNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val rangeFailed = MutableStateFlow(false)

    val state: StateFlow<ListenPanelState> =
        combine(player.state, loadedRange, repeatTimes, reciterNames, rangeFailed) {
                playback,
                range,
                repeat,
                names,
                failed ->
                panelState(playback, range, repeat, names, failed)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                ListenPanelState(),
            )

    init {
        viewModelScope.launch {
            reciterNames.value = reciters.reciters().associate { it.id to it.name }
        }
    }

    /** Plays from [from] to [to] (null: end of the surah) with the selected reciter. */
    fun start(from: AyahRef, to: AyahRef?) {
        viewModelScope.launch {
            val reciterId = settings.selectedReciterId.first()
            val ayahs =
                try {
                    playlistBuilder.build(from, to)
                } catch (@Suppress("SwallowedException") error: IllegalArgumentException) {
                    // The player would report the same range as an error; the panel shows it.
                    rangeFailed.value = true
                    return@launch
                }
            rangeFailed.value = false
            loadedRange.value = LoadedRange(reciterId, ayahs)
            player.setRepeat(repeatTimes.value)
            player.play(reciterId, from, ayahs.last())
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            when (player.state.first()) {
                is PlaybackState.Playing -> player.pause()
                is PlaybackState.Paused -> player.resume()
                else -> Unit
            }
        }
    }

    fun previous() = stepBy(-1)

    fun next() = stepBy(1)

    fun cycleRepeat() {
        val choices = REPEAT_CHOICES
        val nextIndex = (choices.indexOf(repeatTimes.value) + 1) % choices.size
        repeatTimes.value = choices[nextIndex]
        player.setRepeat(repeatTimes.value)
    }

    fun close() {
        player.stop()
        loadedRange.value = null
    }

    private fun stepBy(delta: Int) {
        viewModelScope.launch {
            val range = loadedRange.value ?: return@launch
            val current = currentAyah(player.state.first()) ?: return@launch
            val target =
                range.ayahs.getOrNull(range.ayahs.indexOf(current) + delta) ?: return@launch
            player.play(range.reciterId, target, range.ayahs.last())
        }
    }

    private fun currentAyah(playback: PlaybackState): AyahRef? =
        when (playback) {
            is PlaybackState.Playing -> playback.ayah
            is PlaybackState.Paused -> playback.ayah
            else -> null
        }

    private fun panelState(
        playback: PlaybackState,
        range: LoadedRange?,
        repeat: Int,
        names: Map<String, String>,
        failed: Boolean,
    ): ListenPanelState {
        val reciterName = range?.let { names[it.reciterId] }.orEmpty()
        val ayahs = range?.ayahs.orEmpty()
        return when {
            failed -> ListenPanelState(isVisible = true, hasError = true, repeatTimes = repeat)
            playback is PlaybackState.Error ->
                ListenPanelState(
                    isVisible = true,
                    reciterName = reciterName,
                    hasError = true,
                    repeatTimes = repeat,
                )
            playback is PlaybackState.Playing ->
                ListenPanelState(
                    isVisible = true,
                    reciterName = names[playback.reciterId].orEmpty(),
                    isPlaying = true,
                    ayahNumber = ayahs.indexOf(playback.ayah) + 1,
                    ayahCount = ayahs.size,
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    repeatTimes = repeat,
                )
            playback is PlaybackState.Paused ->
                ListenPanelState(
                    isVisible = true,
                    reciterName = names[playback.reciterId].orEmpty(),
                    ayahNumber = ayahs.indexOf(playback.ayah) + 1,
                    ayahCount = ayahs.size,
                    positionMs = playback.positionMs,
                    repeatTimes = repeat,
                )
            else -> ListenPanelState(repeatTimes = repeat)
        }
    }

    companion object {
        /** The repeat counts the repeat button steps through; 0 is off. */
        val REPEAT_CHOICES = listOf(0, 1, 3, 5, 10)

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Formats a position as `m:ss`, or `h:mm:ss` from one hour. */
fun formatClock(milliseconds: Long): String {
    val totalSeconds = (milliseconds / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
