package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.DownloadProblem
import app.awn.quran.player.PlayStep
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
    data class Idle(
        val reciterName: String,
        val hasError: Boolean,
        /** Why a download failed, when the error has a cause the bar can name. */
        val problem: DownloadProblem? = null,
    ) : AudioBarState

    /** The audio is missing and big: ask whether to download [sizeBytes] or stream. */
    data class Confirm(val sizeBytes: Long, val metered: Boolean) : AudioBarState

    /**
     * Audio plays ([isPlaying]) or is paused. [repeatCount] and [speed] are null at their defaults,
     * so the bar shows a small value only when they differ.
     */
    data class Active(
        val isPlaying: Boolean,
        val repeatCount: Int?,
        val speed: Float?,
        /** The step of a cumulative drill, or null when the range plays in plain order. */
        val step: PlayStep? = null,
    ) : AudioBarState

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
    private val settings: ListenSettings,
    private val controller: AudioController,
    private val script: StateFlow<ScriptId>,
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
        combine(
                player.state,
                player.options,
                settings.selectedReciterId,
                reciterNames,
                player.downloadPrompt,
            ) { playback, options, reciterId, names, prompt ->
                if (prompt != null) {
                    AudioBarState.Confirm(prompt.sizeBytes, prompt.metered)
                } else {
                    barState(playback, options, names[reciterId].orEmpty())
                }
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

    /** Prepares the player when the bar appears, so the first play starts quickly. */
    fun warmUp() {
        viewModelScope.launch { player.warmUp(settings.selectedReciterId.first()) }
    }

    /** Tells the bar which page it is on; Play starts from the first ayah of this page. */
    fun showPage(pageNumber: Int) {
        page.value = pageNumber
        mutableStartChoices.value = emptyList()
        viewModelScope.launch {
            mutablePageStart.value = quranText.ayahsOnPage(pageNumber, script.value).minOrNull()
        }
    }

    /** Resumes a paused ayah, or starts from the page (asking first when it holds two surahs). */
    fun play() {
        viewModelScope.launch {
            if (player.state.first() is PlaybackState.Paused) {
                player.resume()
                return@launch
            }
            val choices = startChoicesFor(quranText.ayahsOnPage(page.value, script.value))
            when (choices.size) {
                0 -> Unit
                1 -> controller.playFrom(choices.single())
                else -> mutableStartChoices.value = choices.map {
                        StartChoice(it, surahNames.value[it.surah].orEmpty())
                    }
            }
        }
    }

    /**
     * The ayah the microphone recites from: the one playing or paused, else the first ayah of the
     * page the bar is on. Null when neither is known yet.
     */
    suspend fun reciteStart(): AyahRef? =
        when (val playback = player.state.first()) {
            is PlaybackState.Playing -> playback.ayah
            is PlaybackState.Paused -> playback.ayah
            else -> mutablePageStart.value
        }

    fun choose(choice: StartChoice) {
        mutableStartChoices.value = emptyList()
        controller.playFrom(choice.ayah)
    }

    fun dismissStartChoices() {
        mutableStartChoices.value = emptyList()
    }

    fun answerDownload(download: Boolean) = player.answerDownloadPrompt(download)

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
            is PlaybackState.Playing -> active(isPlaying = true, options, playback.step)
            is PlaybackState.Paused -> active(isPlaying = false, options, playback.step)
            is PlaybackState.Error ->
                AudioBarState.Idle(reciterName, hasError = true, problem = playback.problem)
            PlaybackState.Idle -> AudioBarState.Idle(reciterName, hasError = false)
        }

    /** The repeat shown is the per-ayah one, else the range one, and only when above 1. */
    private fun active(
        isPlaying: Boolean,
        options: PlaybackOptions,
        step: PlayStep?,
    ): AudioBarState.Active {
        val shownRepeat =
            if (options.repeatEachAyah > 1) options.repeatEachAyah else options.repeatRange
        return AudioBarState.Active(
            isPlaying = isPlaying,
            repeatCount = shownRepeat.takeIf { it > 1 },
            speed = options.speed.takeIf { it != 1f },
            step = step,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
