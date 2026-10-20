package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PauseAfterAyah
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.SleepTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What [PlayRangePanel] shows. [chapters] is empty until the surah list is loaded. */
data class PlayRangeState(
    val selection: RangeSelection?,
    val options: PlaybackOptions,
    val chapters: List<Chapter>,
    val sleepTimer: SleepTimer? = null,
)

/**
 * Drives the Play tab of the ayah panel: a range from one ayah to another, how often to repeat each
 * ayah and the range, and the speed. Repeat and speed apply to the player at once, so they also
 * change what is playing; Play starts the range.
 */
@Suppress("TooManyFunctions") // One setter per control of the panel.
class PlayRangeViewModel(
    private val player: AudioPlayer,
    private val quranText: QuranTextRepository,
    private val controller: AudioController,
    private val script: StateFlow<ScriptId>,
) : ViewModel() {

    private val selection = MutableStateFlow<RangeSelection?>(null)
    private val chapters = MutableStateFlow<List<Chapter>>(emptyList())

    val state: StateFlow<PlayRangeState> =
        combine(selection, player.options, chapters, player.sleepTimer) {
                range,
                options,
                chapterList,
                sleepTimer ->
                PlayRangeState(range, options, chapterList, sleepTimer)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PlayRangeState(null, PlaybackOptions(), emptyList()),
            )

    init {
        viewModelScope.launch { chapters.value = quranText.chapters() }
    }

    /**
     * Sets the range to start at [ayah] and end at the last ayah of [page] (or at [ayah] itself if
     * that ayah is the last one or lies after it).
     */
    fun load(ayah: AyahRef, page: Int) {
        viewModelScope.launch {
            val lastOnPage = quranText.ayahsOnPage(page, script.value).maxOrNull()
            val end = lastOnPage?.takeIf { it >= ayah } ?: ayah
            selection.value = RangeSelection(from = ayah, to = end)
        }
    }

    fun setFromSurah(surah: Int) = update { it.withFrom(AyahRef(surah, 1)) }

    fun setFromAyah(ayahNumber: Int) = update { it.withFrom(AyahRef(it.from.surah, ayahNumber)) }

    /** Ending in another surah ends at that surah's last ayah. */
    fun setToSurah(surah: Int) = update { it.withTo(AyahRef(surah, versesIn(surah))) }

    fun setToAyah(ayahNumber: Int) = update { it.withTo(AyahRef(it.to.surah, ayahNumber)) }

    fun setOnlyRange(onlyRange: Boolean) = update { it.copy(onlyRange = onlyRange) }

    fun setRepeatEachAyah(plays: Int) =
        player.setOptions(player.options.value.copy(repeatEachAyah = plays))

    fun setRepeatRange(plays: Int) =
        player.setOptions(player.options.value.copy(repeatRange = plays))

    fun setPauseAfterAyah(pause: PauseAfterAyah) =
        player.setOptions(player.options.value.copy(pauseAfterAyah = pause))

    fun setCumulative(cumulative: Boolean) =
        player.setOptions(player.options.value.copy(cumulative = cumulative))

    fun setSleepTimer(timer: SleepTimer?) = player.setSleepTimer(timer)

    fun setSpeed(speed: Float) = player.setOptions(player.options.value.copy(speed = speed))

    /** Plays the range; without "only this range" playback goes on to the end of the last surah. */
    fun play() {
        val range = selection.value ?: return
        controller.playRange(range.from, range.to.takeIf { range.onlyRange })
    }

    private fun update(change: (RangeSelection) -> RangeSelection) {
        selection.value = selection.value?.let(change)
    }

    private fun versesIn(surah: Int): Int =
        chapters.value.firstOrNull { it.number == surah }?.versesCount ?: 1

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
