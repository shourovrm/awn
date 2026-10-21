package app.awn.quran.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.Bookmark
import app.awn.quran.core.data.BookmarkRepository
import app.awn.quran.core.data.RecentPage
import app.awn.quran.core.data.SavedAyah
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.learn.content.Snippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

private const val RECENT_PAGE_COUNT = 3
private const val STOP_TIMEOUT_MILLIS = 5_000L

/** A recently opened page with the surah that begins it, for the "Recent pages" rows. */
data class RecentRow(val page: Int, val surah: Int)

/** What the Quran tab and the Bookmarks screen show. [content] is null until the lists are read. */
data class IndexState(
    val content: IndexContent? = null,
    /** The card that opens the page read last; null until the lists are read. */
    val continueReading: ContinueReading? = null,
    /** The page opened last; null on a fresh install. */
    val lastPage: Int? = null,
    /** The surah on [lastPage]; the list marks it and opens scrolled to it. */
    val lastSurah: Int? = null,
    val recentPages: List<RecentRow> = emptyList(),
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val sort: BookmarkSort = BookmarkSort.Date,
    val showSurahNameMeaning: Boolean = true,
    /** Every snippet the app holds; the tab picks the day's one with `SnippetRepository.forDay`. */
    val snippets: List<Snippet> = emptyList(),
)

/** Gathers the Quran tab and the Bookmarks screen from the Quran text and the user's bookmarks. */
@OptIn(ExperimentalCoroutinesApi::class)
class IndexViewModel(
    private val loader: IndexLoader,
    bookmarks: BookmarkRepository,
    showSurahNameMeaning: Flow<Boolean>,
    script: Flow<ScriptId>,
    loadSnippets: () -> List<Snippet>,
) : ViewModel() {

    private val sort = MutableStateFlow(BookmarkSort.Date)

    private val content: Flow<IndexContent> =
        script.distinctUntilChanged().mapLatest { loader.loadContent(it) }

    private val snippets: Flow<List<Snippet>> = flow { emit(loadSnippets()) }.flowOn(Dispatchers.IO)

    val state: StateFlow<IndexState> =
        combine(
                content,
                bookmarks.bookmarks(),
                bookmarks.savedAyahs(),
                bookmarks.recentPages(),
                combine(sort, showSurahNameMeaning, snippets, ::Preferences),
            ) { loadedContent, pageMarks, savedAyahs, recentPages, preferences ->
                Sources(loadedContent, pageMarks, savedAyahs, recentPages, preferences)
            }
            .mapLatest(::toState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                IndexState(),
            )

    fun setSort(newSort: BookmarkSort) {
        sort.value = newSort
    }

    private class Preferences(
        val sort: BookmarkSort,
        val showMeaning: Boolean,
        val snippets: List<Snippet>,
    )

    private class Sources(
        val content: IndexContent,
        val pageMarks: List<Bookmark>,
        val savedAyahs: List<SavedAyah>,
        val recentPages: List<RecentPage>,
        val preferences: Preferences,
    )

    private suspend fun toState(sources: Sources): IndexState {
        val script = sources.content.script
        val lastPage = sources.recentPages.firstOrNull()?.page
        val entries =
            sources.pageMarks.map {
                BookmarkEntry.PageMark(it.page, it.createdAt, loader.surahOnPage(it.page, script))
            } + sources.savedAyahs.map { loader.toAyahMark(it, script) }
        return IndexState(
            content = sources.content,
            continueReading = loader.continueReading(lastPage, sources.content),
            lastPage = lastPage,
            lastSurah = lastPage?.let { loader.surahOnPage(it, script) },
            recentPages =
                sources.recentPages.take(RECENT_PAGE_COUNT).map {
                    RecentRow(it.page, loader.surahOnPage(it.page, script))
                },
            bookmarks = sortBookmarks(entries, sources.preferences.sort),
            sort = sources.preferences.sort,
            showSurahNameMeaning = sources.preferences.showMeaning,
            snippets = sources.preferences.snippets,
        )
    }
}
