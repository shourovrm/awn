package app.awn.quran.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.player.AudioDownloads
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The reciters in the sheet's three groups; a reciter appears in the first group that fits. */
data class ReciterGroups(
    val downloadedForPage: List<Reciter>,
    val withDownloads: List<Reciter>,
    val all: List<Reciter>,
)

data class ReciterSheetState(
    val groups: ReciterGroups,
    val selectedId: String,
    val query: String,
)

/**
 * Splits [reciters] into the sheet's groups after keeping those whose name contains [query]
 * (ignoring case). A reciter with the whole page downloaded is listed only under the page group,
 * one with other downloads only under "with downloads", so nobody is listed twice.
 */
internal fun groupReciters(
    reciters: List<Reciter>,
    idsDownloadedForPage: Set<String>,
    idsWithDownloads: Set<String>,
    query: String,
): ReciterGroups {
    val matching = reciters.filter { query.isBlank() || it.name.contains(query.trim(), true) }
    return ReciterGroups(
        downloadedForPage = matching.filter { it.id in idsDownloadedForPage },
        withDownloads =
            matching.filter { it.id in idsWithDownloads && it.id !in idsDownloadedForPage },
        all = matching.filter { it.id !in idsDownloadedForPage && it.id !in idsWithDownloads },
    )
}

/** Drives the reciter sheet that opens from the audio bar and from the Audio settings page. */
class ReciterSheetViewModel(
    private val reciters: ReciterRepository,
    private val downloads: AudioDownloads,
    private val quranText: QuranTextRepository,
    private val settings: ListenSettings,
    private val script: ScriptId,
) : ViewModel() {

    private val reciterList = MutableStateFlow<List<Reciter>>(emptyList())
    private val idsDownloadedForPage = MutableStateFlow<Set<String>>(emptySet())
    private val idsWithDownloads = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")

    val state: StateFlow<ReciterSheetState> =
        combine(
                reciterList,
                idsDownloadedForPage,
                idsWithDownloads,
                query,
                settings.selectedReciterId,
            ) { list, forPage, withDownloads, text, selectedId ->
                ReciterSheetState(
                    groups = groupReciters(list, forPage, withDownloads, text),
                    selectedId = selectedId,
                    query = text,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                ReciterSheetState(ReciterGroups(emptyList(), emptyList(), emptyList()), "", ""),
            )

    /** Reads the reciters and what each has downloaded; [page] is null outside the Quran page. */
    fun load(page: Int?) {
        viewModelScope.launch {
            val list = reciters.reciters()
            reciterList.value = list
            idsWithDownloads.value =
                list.filter { downloads.bytesUsed(it.id) > 0L }.map { it.id }.toSet()
            val pageAyahs = page?.let { quranText.ayahsOnPage(it, script) }.orEmpty()
            idsDownloadedForPage.value =
                if (pageAyahs.isEmpty()) {
                    emptySet()
                } else {
                    list
                        .filter { downloads.status(it.id, pageAyahs).complete }
                        .map { it.id }
                        .toSet()
                }
        }
    }

    fun search(text: String) {
        query.value = text
    }

    fun select(reciterId: String) {
        viewModelScope.launch { settings.selectReciter(reciterId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
