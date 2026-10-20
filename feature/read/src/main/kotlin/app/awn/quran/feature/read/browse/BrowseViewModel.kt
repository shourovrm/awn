package app.awn.quran.feature.read.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Bookmark
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.RecentPage
import app.awn.quran.core.data.SavedAyah
import app.awn.quran.feature.read.ReadDependencies
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

/** A surah in the list with the page it starts on. */
data class SurahRow(val chapter: Chapter, val page: Int)

/** The outcome of the last export or import, shown once as a line of text. */
enum class TransferStatus {
    Exported,
    Imported,
    ExportFailed,
    ImportFailed,
}

data class BrowseUiState(
    val surahs: List<SurahRow> = emptyList(),
    val pageCount: Int = 604,
    val bookmarks: List<Bookmark> = emptyList(),
    val savedAyahs: List<SavedAyah> = emptyList(),
    val recentPages: List<RecentPage> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val transferStatus: TransferStatus? = null,
)

/** Drives the lists: surahs, juz, go to page, bookmarks, saved ayahs and recent pages. */
class BrowseViewModel(private val dependencies: ReadDependencies) : ViewModel() {

    private val mutableState = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = mutableState

    init {
        viewModelScope.launch { loadSurahs() }
        viewModelScope.launch {
            dependencies.bookmarks.bookmarks().collect { list ->
                mutableState.update { it.copy(bookmarks = list) }
            }
        }
        viewModelScope.launch {
            dependencies.bookmarks.savedAyahs().collect { list ->
                mutableState.update { it.copy(savedAyahs = list) }
            }
        }
        viewModelScope.launch {
            dependencies.bookmarks.recentPages().collect { list ->
                mutableState.update { it.copy(recentPages = list) }
            }
        }
    }

    /** Reads a page number typed by the user; null unless it is a page of the book. */
    fun parsePage(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..mutableState.value.pageCount }

    /** Adds or removes the bookmark of [page]. */
    fun toggleBookmark(page: Int) {
        viewModelScope.launch { dependencies.bookmarks.toggleBookmark(page) }
    }

    fun removeSavedAyah(ayah: AyahRef) {
        viewModelScope.launch { dependencies.bookmarks.removeSavedAyah(ayah) }
    }

    /** Returns the page that holds the first word of [ayah] in the chosen script. */
    suspend fun pageOf(ayah: AyahRef): Int {
        val script = dependencies.settingsStore.settings.first().script
        return dependencies.quranText.pageOf(ayah, script)
    }

    /** Writes the export document through [write], which the caller backs with a file. */
    fun export(write: suspend (String) -> Unit) {
        viewModelScope.launch {
            val status =
                try {
                    write(dependencies.bookmarks.exportJson())
                    TransferStatus.Exported
                } catch (_: IOException) {
                    TransferStatus.ExportFailed
                }
            mutableState.update { it.copy(transferStatus = status) }
        }
    }

    /** Merges the document that [read] returns into the stored bookmarks. */
    fun import(read: suspend () -> String) {
        viewModelScope.launch {
            val status =
                try {
                    dependencies.bookmarks.importJson(read())
                    TransferStatus.Imported
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    TransferStatus.ImportFailed
                } catch (_: IllegalArgumentException) {
                    // The repository rejects malformed documents by throwing; which type depends
                    // on its parser, so the two common ones are handled.
                    TransferStatus.ImportFailed
                } catch (_: JSONException) {
                    TransferStatus.ImportFailed
                }
            mutableState.update { it.copy(transferStatus = status) }
        }
    }

    fun clearTransferStatus() {
        mutableState.update { it.copy(transferStatus = null) }
    }

    private suspend fun loadSurahs() {
        val script = dependencies.settingsStore.settings.first().script
        val chapters = dependencies.quranText.chapters()
        val rows = chapters.map { chapter ->
            SurahRow(chapter, dependencies.quranText.pageOf(AyahRef(chapter.number, 1), script))
        }
        mutableState.update {
            it.copy(
                surahs = rows,
                chapters = chapters,
                pageCount = dependencies.quranText.pageCount(script),
            )
        }
    }
}
