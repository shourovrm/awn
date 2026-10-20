package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.player.AudioDownloads
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
)

/** Drives the Audio settings page. */
class AudioSettingsViewModel(
    reciters: ReciterRepository,
    downloads: AudioDownloads,
    private val settings: ListenSettings,
) : ViewModel() {

    private val reciterNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val totalUsedBytes = MutableStateFlow(0L)

    val state: StateFlow<AudioSettingsState> =
        combine(
                reciterNames,
                settings.selectedReciterId,
                settings.downloadAmount,
                settings.wifiOnly,
                totalUsedBytes,
            ) { names, reciterId, amount, wifiOnly, used ->
                AudioSettingsState(names[reciterId].orEmpty(), amount, wifiOnly, used)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AudioSettingsState(),
            )

    init {
        viewModelScope.launch {
            val list = reciters.reciters()
            reciterNames.value = list.associate { it.id to it.name }
            if (list.isEmpty()) return@launch
            combine(list.map { downloads.usedBytes(it.id) }) { sizes -> sizes.sum() }
                .collect { totalUsedBytes.value = it }
        }
    }

    fun setDownloadAmount(amount: DownloadAmount) {
        viewModelScope.launch { settings.setDownloadAmount(amount) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(wifiOnly) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
