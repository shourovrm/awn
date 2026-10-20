package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.player.AudioDownloads
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

/**
 * The reciter list: choose a reciter, see and remove its downloads, set Wi-Fi only.
 *
 * The size of each reciter's downloads follows [AudioDownloads.usedBytes], so it changes while a
 * download runs. Reading it once, when this view model was created, left "Nothing downloaded" on
 * screen after a download that finished later (defect 4 of the phone test).
 */
class RecitersViewModel(
    private val reciters: ReciterRepository,
    private val downloads: AudioDownloads,
    private val settings: ListenSettings,
) : ViewModel() {

    private val reciterList = MutableStateFlow<List<Reciter>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RecitersState> =
        reciterList
            .flatMapLatest { list ->
                combine(
                    usedBytesOf(list),
                    settings.selectedReciterId,
                    settings.wifiOnly,
                ) { bytes, selectedId, wifiOnly ->
                    RecitersState(
                        rows =
                            list.mapIndexed { index, reciter ->
                                ReciterRow(
                                    id = reciter.id,
                                    name = reciter.name,
                                    wordByWord = reciter.wordByWord,
                                    downloadedBytes = bytes[index],
                                    isSelected = reciter.id == selectedId,
                                )
                            },
                        wifiOnly = wifiOnly,
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                RecitersState(),
            )

    init {
        refresh()
    }

    /** Reads the reciter list again. */
    fun refresh() {
        viewModelScope.launch { reciterList.value = reciters.reciters() }
    }

    fun select(reciterId: String) {
        viewModelScope.launch { settings.selectReciter(reciterId) }
    }

    fun removeDownloads(reciterId: String) {
        viewModelScope.launch { downloads.remove(reciterId) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(wifiOnly) }
    }

    private fun usedBytesOf(list: List<Reciter>) =
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { downloads.usedBytes(it.id) }) { sizes -> sizes.toList() }
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
