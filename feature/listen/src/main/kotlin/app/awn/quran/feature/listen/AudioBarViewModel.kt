package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three looks of the audio bar. */
sealed interface AudioBarState {

    /** Nothing plays. [hasError] is true after the last request failed. */
    data class Idle(val reciterName: String, val hasError: Boolean) : AudioBarState

    /**
     * Audio plays ([isPlaying]) or is paused. [repeatCount] and [speed] are null at their defaults,
     * so the bar shows a small value only when they differ.
     */
    data class Active(val isPlaying: Boolean, val repeatCount: Int?, val speed: Float?) :
        AudioBarState

    data class Downloading(val doneBytes: Long, val totalBytes: Long?) : AudioBarState
}

/** One surah a page can start playing from. */
data class StartChoice(val ayah: AyahRef, val surahName: String)

/** Drives the audio bar of one Quran page at a time. */
@Suppress("TooManyFunctions") // One function per button of the bar.
class AudioBarViewModel(
    private val player: AudioPlayer,
    reciters: ReciterRepository,
    private val quranText: QuranTextRepository,
    settings: ListenSettings,
    private val controller: AudioController,
    private val script: ScriptId,
) : ViewModel() {

    private val page = MutableStateFlow(1)
    private val reciterNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val surahNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    private val mutableStartChoices = MutableStateFlow<List<StartChoice>>(emptyList())

    private val mutablePageStart = MutableStateFlow<AyahRef?>(null)

    /** The first ayah of the page the bar is on, where the play options open. */
    val pageStart: StateFlow<AyahRef?> = mutablePageStart

    /** Not empty while the bar asks "Start from" because the page holds two surahs. */
    val startChoices: StateFlow<List<StartChoice>> = mutableStartChoices

    val state: StateFlow<AudioBarState> =
        combine(player.state, player.options, settings.selectedReciterId, reciterNames) {
                playback,
                options,
                reciterId,
                names ->
                barState(playback, options, names[reciterId].orEmpty())
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AudioBarState.Idle(reciterName = "", hasError = false),
            )

    init {
        viewModelScope.launch {
            reciterNames.value = reciters.reciters().associate { it.id to it.name }
            surahNames.value = quranText.chapters().associate { it.number to it.nameSimple }
        }
    }

    /** Tells the bar which page it is on; Play starts from the first ayah of this page. */
    fun showPage(pageNumber: Int) {
        page.value = pageNumber
        mutableStartChoices.value = emptyList()
        viewModelScope.launch {
            mutablePageStart.value = quranText.ayahsOnPage(pageNumber, script).minOrNull()
        }
    }

    /** Resumes a paused ayah, or starts from the page (asking first when it holds two surahs). */
    fun play() {
        viewModelScope.launch {
            if (player.state.first() is PlaybackState.Paused) {
                player.resume()
                return@launch
            }
            val choices = startChoicesFor(quranText.ayahsOnPage(page.value, script))
            when (choices.size) {
                0 -> Unit
                1 -> controller.playFrom(choices.single())
                else -> mutableStartChoices.value = choices.map {
                        StartChoice(it, surahNames.value[it.surah].orEmpty())
                    }
            }
        }
    }

    fun choose(choice: StartChoice) {
        mutableStartChoices.value = emptyList()
        controller.playFrom(choice.ayah)
    }

    fun dismissStartChoices() {
        mutableStartChoices.value = emptyList()
    }

    fun pause() = player.pause()

    fun stop() = player.stop()

    fun previous() = player.previous()

    fun next() = player.next()

    fun cycleRepeat() {
        val options = player.options.value
        player.setOptions(options.copy(repeatEachAyah = nextRepeat(options.repeatEachAyah)))
    }

    fun cycleSpeed() {
        val options = player.options.value
        player.setOptions(options.copy(speed = nextSpeed(options.speed)))
    }

    private fun barState(
        playback: PlaybackState,
        options: PlaybackOptions,
        reciterName: String,
    ): AudioBarState =
        when (playback) {
            is PlaybackState.Downloading ->
                AudioBarState.Downloading(playback.doneBytes, playback.totalBytes)
            is PlaybackState.Playing -> active(isPlaying = true, options)
            is PlaybackState.Paused -> active(isPlaying = false, options)
            is PlaybackState.Error -> AudioBarState.Idle(reciterName, hasError = true)
            PlaybackState.Idle -> AudioBarState.Idle(reciterName, hasError = false)
        }

    /** The repeat shown is the per-ayah one, else the range one, and only when above 1. */
    private fun active(isPlaying: Boolean, options: PlaybackOptions): AudioBarState.Active {
        val shownRepeat =
            if (options.repeatEachAyah > 1) options.repeatEachAyah else options.repeatRange
        return AudioBarState.Active(
            isPlaying = isPlaying,
            repeatCount = shownRepeat.takeIf { it > 1 },
            speed = options.speed.takeIf { it != 1f },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
