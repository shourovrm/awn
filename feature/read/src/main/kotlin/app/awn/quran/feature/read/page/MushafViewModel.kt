package app.awn.quran.feature.read.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.ReadSettings
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The state of one page slot of the pager. */
sealed interface PageLoad {
    data object Loading : PageLoad

    data class Loaded(val layout: PageLayout) : PageLoad

    data object Failed : PageLoad
}

/** One translation of the ayah in the sheet. [rangeLabel] is set when a passage covers several. */
data class SheetTranslation(val name: String, val rangeLabel: String?, val text: String)

/** The sheet that rises when the user taps a word. */
data class AyahSheet(
    val ayah: AyahRef,
    val translations: List<SheetTranslation>,
    val isSaved: Boolean,
)

data class MushafUiState(
    /** False until the first page is known, so the pager opens on the right page at once. */
    val hasStarted: Boolean = false,
    val currentPage: Int = 1,
    val pageCount: Int = DEFAULT_PAGE_COUNT,
    val settings: ReadSettings = ReadSettings(),
    val pages: Map<Int, PageLoad> = emptyMap(),
    val chapters: List<Chapter> = emptyList(),
    val headerSurah: Chapter? = null,
    val juz: Int = 1,
    val isBookmarked: Boolean = false,
    val selectedWord: WordKey? = null,
    val sheet: AyahSheet? = null,
    /** Progress 0 to 1 of the glyph font download, or null when none runs. */
    val fontDownloadProgress: Float? = null,
    val fontDownloadFailed: Boolean = false,
    /** Changes whenever a pack is installed or removed, so font lookups are repeated. */
    val packRevision: Int = 0,
) {
    companion object {
        const val DEFAULT_PAGE_COUNT = 604
    }
}

/**
 * Drives the pager of mushaf pages: page loading, word taps, bookmarks and the font download. Each
 * public function is one user action, so the function count follows the screen, not the logic.
 */
@Suppress("TooManyFunctions")
class MushafViewModel(private val dependencies: ReadDependencies) : ViewModel() {

    private val mutableState = MutableStateFlow(MushafUiState())
    val state: StateFlow<MushafUiState> = mutableState

    private var lastReportedPage: Int? = null
    private var bookmarkedPageSet: Set<Int> = emptySet()
    private var latestSavedAyahs: Set<AyahRef> = emptySet()

    init {
        viewModelScope.launch { loadChapters() }
        viewModelScope.launch { followSettings() }
        viewModelScope.launch { followBookmarks() }
        viewModelScope.launch { followSavedAyahs() }
        viewModelScope.launch { followInstalledPacks() }
    }

    /**
     * Opens the reader on [requestedPage], or on the most recently opened page when it is null, or
     * on page 1 when nothing was opened yet.
     */
    fun start(requestedPage: Int?) {
        viewModelScope.launch {
            val page =
                requestedPage ?: dependencies.bookmarks.recentPages().first().firstOrNull()?.page
            onPageSettled(page ?: 1)
        }
    }

    /** Called when the pager has come to rest on [page]. */
    fun onPageSettled(page: Int) {
        val pageCount = mutableState.value.pageCount
        val validPage = page.coerceIn(1, pageCount)
        mutableState.update {
            it.copy(
                hasStarted = true,
                currentPage = validPage,
                juz = JuzTable.juzOfPage(validPage),
                selectedWord = null,
                sheet = null,
                headerSurah = headerSurahFor(it.pages[validPage], it.chapters),
            )
        }
        loadPage(validPage)
        loadPage(validPage - 1)
        loadPage(validPage + 1)
        reportPageOpened(validPage)
        syncBookmarkFlag()
    }

    /** Starts loading [page] unless it is already loaded or loading. */
    fun loadPage(page: Int) {
        val state = mutableState.value
        if (page !in 1..state.pageCount || state.pages[page] != null) return
        mutableState.update { it.copy(pages = it.pages + (page to PageLoad.Loading)) }
        viewModelScope.launch {
            val load = fetchLayout(page, state.settings.script)
            mutableState.update { current ->
                val pages = current.pages + (page to load)
                val header =
                    if (page == current.currentPage) {
                        headerSurahFor(load, current.chapters)
                    } else {
                        current.headerSurah
                    }
                current.copy(pages = pages, headerSurah = header)
            }
        }
    }

    /** Selects [key] and opens the sheet with its ayah's translations; a second tap closes it. */
    fun onWordTapped(key: WordKey) {
        if (mutableState.value.selectedWord == key) {
            dismissSheet()
            return
        }
        mutableState.update { it.copy(selectedWord = key) }
        viewModelScope.launch { showSheetFor(key) }
    }

    fun dismissSheet() {
        mutableState.update { it.copy(selectedWord = null, sheet = null) }
    }

    fun toggleBookmark() {
        val page = mutableState.value.currentPage
        viewModelScope.launch { dependencies.bookmarks.toggleBookmark(page) }
    }

    /** Saves the ayah in the sheet, or removes it from the saved ayahs when it is saved. */
    fun toggleSavedAyah() {
        val sheet = mutableState.value.sheet ?: return
        viewModelScope.launch {
            if (sheet.isSaved) {
                dependencies.bookmarks.removeSavedAyah(sheet.ayah)
            } else {
                dependencies.bookmarks.saveAyah(sheet.ayah, note = null)
            }
        }
    }

    /** Downloads the font pack of the chosen script; progress and failure show in the state. */
    fun downloadFont() {
        val script = mutableState.value.settings.script
        viewModelScope.launch {
            val entry =
                dependencies.packs.manifest(MANIFEST_SCRIPTS).firstOrNull { it.id == script.packId }
            if (entry == null) {
                mutableState.update { it.copy(fontDownloadFailed = true) }
                return@launch
            }
            mutableState.update { it.copy(fontDownloadProgress = 0f, fontDownloadFailed = false) }
            try {
                dependencies.packs.install(entry) { progress ->
                    mutableState.update { it.copy(fontDownloadProgress = progress) }
                }
                mutableState.update { it.copy(fontDownloadProgress = null) }
            } catch (_: IOException) {
                // A dropped connection is shown as a failed state; the user can try again.
                mutableState.update {
                    it.copy(fontDownloadProgress = null, fontDownloadFailed = true)
                }
            }
        }
    }

    private suspend fun loadChapters() {
        val chapters = dependencies.quranText.chapters()
        mutableState.update {
            it.copy(
                chapters = chapters,
                headerSurah = headerSurahFor(it.pages[it.currentPage], chapters),
            )
        }
    }

    private suspend fun followSettings() {
        dependencies.settingsStore.settings.distinctUntilChanged().collect { settings ->
            val scriptChanged = settings.script != mutableState.value.settings.script
            mutableState.update {
                if (scriptChanged) {
                    it.copy(settings = settings, pages = emptyMap())
                } else {
                    it.copy(settings = settings)
                }
            }
            if (scriptChanged) {
                val page = mutableState.value.currentPage
                loadPage(page)
                loadPage(page - 1)
                loadPage(page + 1)
            }
        }
    }

    private suspend fun followBookmarks() {
        dependencies.bookmarks
            .bookmarks()
            .map { bookmarks -> bookmarks.map { it.page }.toSet() }
            .collect { bookmarkedPages ->
                bookmarkedPageSet = bookmarkedPages
                syncBookmarkFlag()
            }
    }

    private fun syncBookmarkFlag() {
        mutableState.update { it.copy(isBookmarked = it.currentPage in bookmarkedPageSet) }
    }

    private suspend fun followSavedAyahs() {
        dependencies.bookmarks.savedAyahs().collect { savedAyahs ->
            val savedRefs = savedAyahs.map { it.ayah }.toSet()
            mutableState.update { state ->
                val sheet = state.sheet
                state.copy(sheet = sheet?.copy(isSaved = sheet.ayah in savedRefs))
            }
            latestSavedAyahs = savedRefs
        }
    }

    private suspend fun followInstalledPacks() {
        dependencies.packs.installed().collect {
            mutableState.update { it.copy(packRevision = it.packRevision + 1) }
        }
    }

    private fun reportPageOpened(page: Int) {
        if (lastReportedPage == page) return
        lastReportedPage = page
        viewModelScope.launch {
            dependencies.progressEvents.pageOpened(page)
            dependencies.bookmarks.recordPageOpened(page)
        }
    }

    private suspend fun showSheetFor(key: WordKey) {
        val ayah = AyahRef(key.surah, key.ayah)
        val names = dependencies.translations.available().associate { it.id to it.name }
        val translations =
            mutableState.value.settings.translations.mapNotNull { id ->
                val passage = dependencies.translations.text(id, ayah) ?: return@mapNotNull null
                val isRange = passage.ayahFrom != passage.ayahTo
                SheetTranslation(
                    name = names[id] ?: id.id,
                    rangeLabel = if (isRange) "${passage.ayahFrom}-${passage.ayahTo}" else null,
                    text = passage.text,
                )
            }
        mutableState.update { state ->
            // The user may have tapped another word or closed the sheet while the text loaded.
            if (state.selectedWord != key) {
                state
            } else {
                state.copy(
                    sheet = AyahSheet(ayah, translations, isSaved = ayah in latestSavedAyahs),
                )
            }
        }
    }

    /**
     * Reads [page] in [script]; when that print is not installed the Unicode Hafs layout stands in,
     * so the reader always has something to read. [PageLoad.Failed] is shown to the user as an
     * unavailable page.
     */
    private suspend fun fetchLayout(page: Int, script: ScriptId): PageLoad {
        val layout =
            layoutOrNull(page, script)
                ?: if (script == ScriptId.QpcHafs) null else layoutOrNull(page, ScriptId.QpcHafs)
        return if (layout == null) PageLoad.Failed else PageLoad.Loaded(layout)
    }

    /** The repository throws when a script pack is missing or the page is out of range. */
    private suspend fun layoutOrNull(page: Int, script: ScriptId): PageLayout? =
        try {
            dependencies.quranText.pageLayout(page, script)
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val MANIFEST_SCRIPTS = "scripts"
    }
}

/** The surah of the first word on the page, which is the one the header names. */
internal fun headerSurahFor(load: PageLoad?, chapters: List<Chapter>): Chapter? {
    val layout = (load as? PageLoad.Loaded)?.layout
    val surahNumber =
        layout
            ?.lines
            ?.flatMap { it.items }
            ?.firstNotNullOfOrNull { item ->
                when (item) {
                    is LineItem.WordItem -> item.word.key.surah
                    is LineItem.AyahEnd -> item.ayah.surah
                    is LineItem.SurahHeader,
                    is LineItem.Basmala -> null
                }
            }
    return chapters.firstOrNull { it.number == surahNumber }
}
