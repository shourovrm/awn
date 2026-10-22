package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.DownloadAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Audio settings page shows. [usedBytes] is the audio on disk for every reciter. */
data class AudioSettingsState(
    val reciterName: String = "",
    val downloadAmount: DownloadAmount = DownloadAmount.Page,
    val wifiOnly: Boolean = true,
    val usedBytes: Long = 0L,
    val stopOnTaskRemoved: Boolean = true,
    /** The reciters that have word timings, the only ones the word audio can use. */
    val wordReciters: List<Reciter> = emptyList(),
    /** The id of the word reciter in use; null when no reciter has word timings. */
    val wordReciterId: String? = null,
)

/** Drives the Audio settings page. */
class AudioSettingsViewModel(
    reciters: ReciterRepository,
    downloads: AudioDownloads,
    private val settings: ListenSettings,
    private val player: AudioPlayer,
) : ViewModel() {

    private val allReciters = MutableStateFlow<List<Reciter>>(emptyList())
    private val totalUsedBytes = MutableStateFlow(0L)

    private val wifiOnlyAndOptions =
        combine(settings.wifiOnly, player.options) { wifiOnly, options ->
            wifiOnly to options
        }

    private val reciterChoices =
        combine(allReciters, settings.selectedReciterId, settings.wordReciterId) {
            reciters,
            listeningId,
            chosenWordId ->
            ReciterChoices(reciters, listeningId, chosenWordId)
        }

    val state: StateFlow<AudioSettingsState> =
        combine(
                reciterChoices,
                settings.downloadAmount,
                wifiOnlyAndOptions,
                totalUsedBytes,
            ) { choices, amount, (wifiOnly, options), used ->
                val wordReciters =
                    choices.reciters.filter { it.wordByWord && it.timingPack != null }
                AudioSettingsState(
                    reciterName =
                        choices.reciters
                            .firstOrNull { it.id == choices.listeningId }
                            ?.name
                            .orEmpty(),
                    downloadAmount = amount,
                    wifiOnly = wifiOnly,
                    usedBytes = used,
                    stopOnTaskRemoved = options.stopOnTaskRemoved,
                    wordReciters = wordReciters,
                    wordReciterId = wordReciterIdInUse(choices, wordReciters),
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AudioSettingsState(),
            )

    init {
        viewModelScope.launch {
            val list = reciters.reciters()
            allReciters.value = list
            if (list.isEmpty()) return@launch
            combine(list.map { downloads.usedBytes(it.id) }) { sizes -> sizes.sum() }
                .collect { totalUsedBytes.value = it }
        }
    }

    fun setWordReciter(reciter: Reciter) {
        viewModelScope.launch { settings.setWordReciter(reciter.id) }
    }

    fun setDownloadAmount(amount: DownloadAmount) {
        viewModelScope.launch { settings.setDownloadAmount(amount) }
    }

    fun setStopOnTaskRemoved(stop: Boolean) =
        player.setOptions(player.options.value.copy(stopOnTaskRemoved = stop))

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(wifiOnly) }
    }

    private class ReciterChoices(
        val reciters: List<Reciter>,
        val listeningId: String,
        val chosenWordId: String?,
    )

    private fun wordReciterIdInUse(choices: ReciterChoices, wordReciters: List<Reciter>): String? =
        wordReciterIdInUse(choices.chosenWordId, choices.listeningId, wordReciters.map { it.id })

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The reciter whose voice plays a single word, in the same order as the word card: the chosen word
 * reciter, the listening one, then the first with word timings. Null when [wordReciterIds] is
 * empty.
 */
internal fun wordReciterIdInUse(
    chosenWordId: String?,
    listeningId: String,
    wordReciterIds: List<String>,
): String? =
    when {
        chosenWordId in wordReciterIds -> chosenWordId
        listeningId in wordReciterIds -> listeningId
        else -> wordReciterIds.firstOrNull()
    }
