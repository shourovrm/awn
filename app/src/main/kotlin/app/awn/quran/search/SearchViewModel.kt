package app.awn.quran.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.search.SearchHit
import app.awn.quran.core.data.search.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

private const val TYPING_PAUSE_MILLIS = 250L
private const val STOP_TIMEOUT_MILLIS = 5_000L

/** One result row: the hit with where it is in the Quran. */
data class SearchRow(val hit: SearchHit, val surahName: String, val page: Int)

/** [rows] is null until a query has been searched; an empty list means nothing matched. */
data class SearchUiState(
    val query: String = "",
    val rows: List<SearchRow>? = null,
    /** Surahs whose name or number matches [query]. */
    val surahs: List<Chapter> = emptyList(),
)

/** Searches while the user types, after a short pause so each letter does not start a search. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val search: SearchRepository,
    private val quranText: QuranTextRepository,
    private val script: ScriptId,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val pageCache = mutableMapOf<AyahRef, Int>()

    val state: StateFlow<SearchUiState> =
        query
            .debounce(TYPING_PAUSE_MILLIS)
            .mapLatest { stateFor(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                SearchUiState(),
            )

    /** The text now in the search field, which is ahead of [state] by the typing pause. */
    val typedQuery: StateFlow<String> = query

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }

    private suspend fun stateFor(text: String): SearchUiState {
        val surahs = surahsMatching(text, quranText.chapters())
        return SearchUiState(text, rowsFor(text), surahs)
    }

    /** The page a surah starts on, for the surah rows. */
    suspend fun firstPageOf(surah: Int): Int = pageOf(AyahRef(surah, 1))

    private suspend fun pageOf(ayah: AyahRef): Int =
        pageCache.getOrPut(ayah) { quranText.pageOf(ayah, script) }

    private suspend fun rowsFor(text: String): List<SearchRow>? {
        if (text.isBlank()) return null
        val names = quranText.chapters().associate { it.number to it.nameSimple }
        return search.search(text).hits.map { hit ->
            SearchRow(
                hit = hit,
                surahName = names[hit.ayah.surah].orEmpty(),
                page = pageOf(hit.ayah),
            )
        }
    }
}
