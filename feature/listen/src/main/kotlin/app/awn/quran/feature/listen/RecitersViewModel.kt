package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.player.AudioDownloads
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReciterRow(
    val id: String,
    val name: String,
    val wordByWord: Boolean,
    val downloadedBytes: Long,
    val isSelected: Boolean,
)

data class RecitersState(val rows: List<ReciterRow> = emptyList(), val wifiOnly: Boolean = true)

/** The reciter list: choose a reciter, see and remove its downloads, set Wi-Fi only. */
class RecitersViewModel(
    private val reciters: ReciterRepository,
    private val downloads: AudioDownloads,
    private val settings: ListenSettings,
) : ViewModel() {

    private val reciterList = MutableStateFlow<List<Reciter>>(emptyList())
    private val downloadedBytes = MutableStateFlow<Map<String, Long>>(emptyMap())

    val state: StateFlow<RecitersState> =
        combine(reciterList, downloadedBytes, settings.selectedReciterId, settings.wifiOnly) {
                list,
                bytes,
                selectedId,
                wifiOnly ->
                RecitersState(
                    rows =
                        list.map { reciter ->
                            ReciterRow(
                                id = reciter.id,
                                name = reciter.name,
                                wordByWord = reciter.wordByWord,
                                downloadedBytes = bytes[reciter.id] ?: 0L,
                                isSelected = reciter.id == selectedId,
                            )
                        },
                    wifiOnly = wifiOnly,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                RecitersState(),
            )

    init {
        refresh()
    }

    /** Reads the reciter list and the size of each reciter's downloads again. */
    fun refresh() {
        viewModelScope.launch {
            val list = reciters.reciters()
            reciterList.value = list
            downloadedBytes.value = list.associate { it.id to downloads.bytesUsed(it.id) }
        }
    }

    fun select(reciterId: String) {
        viewModelScope.launch { settings.selectReciter(reciterId) }
    }

    fun removeDownloads(reciterId: String) {
        viewModelScope.launch {
            downloads.remove(reciterId)
            downloadedBytes.value =
                downloadedBytes.value + (reciterId to downloads.bytesUsed(reciterId))
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(wifiOnly) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
