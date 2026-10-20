package app.awn.quran.feature.read.ayah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.dock.dockModelOf
import app.awn.quran.feature.read.page.JuzTable
import app.awn.quran.feature.read.settings.ReadSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the ayah-by-ayah view lists: a whole surah, or the ayahs printed on one page. */
sealed interface AyahListSource {
    data class Surah(val number: Int) : AyahListSource

    data class Page(val number: Int) : AyahListSource
}

/** One word with its meaning, which is null when the word-by-word pack is not installed. */
data class AyahWord(val arabic: String, val meaning: String?)

data class AyahTranslation(val name: String, val rangeLabel: String?, val text: String)

/** Everything shown for one ayah; the view hides the lines the user switched off. */
data class AyahBlock(
    val ayah: AyahRef,
    val words: List<AyahWord>,
    val transliteration: String?,
    val translations: List<AyahTranslation>,
)

data class AyahListUiState(
    val source: AyahListSource,
    val ayahs: List<AyahRef> = emptyList(),
    val blocks: Map<AyahRef, AyahBlock> = emptyMap(),
    val settings: ReadSettings = ReadSettings(),
    val headerSurah: Chapter? = null,
    val juz: Int = 1,
    val page: Int = 1,
    val isBookmarked: Boolean = false,
    /** The ayah being played, or else the one the user last tapped. */
    val currentAyah: AyahRef? = null,
)

/**
 * Drives the ayah-by-ayah view. [wordLanguage] is "en" or "bn" and picks the word-by-word pack;
 * blocks are loaded one ayah at a time as the list scrolls to them.
 */
@Suppress("TooManyFunctions") // Each public function is one user action of the screen.
class AyahListViewModel(
    private val dependencies: ReadDependencies,
    initialSource: AyahListSource,
    private val wordLanguage: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AyahListUiState(source = initialSource))
    val state: StateFlow<AyahListUiState> = mutableState

    private var chapters: List<Chapter> = emptyList()
    private var playingAyah: AyahRef? = null
    private var tappedAyah: AyahRef? = null
    private var bookmarkedPages: Set<Int> = emptySet()

    init {
        viewModelScope.launch {
            chapters = dependencies.quranText.chapters()
            val storedSettings = dependencies.settingsStore.settings.first()
            mutableState.update { it.copy(settings = storedSettings) }
            openSource(initialSource)
        }
        viewModelScope.launch { followSettings() }
        viewModelScope.launch { followAudio() }
        viewModelScope.launch { followBookmarks() }
    }

    /** Lists [source] and reports the page when it is one. */
    fun openSource(source: AyahListSource) {
        viewModelScope.launch {
            val script = mutableState.value.settings.script
            val ayahs =
                when (source) {
                    is AyahListSource.Surah -> ayahsOfSurah(source.number)
                    is AyahListSource.Page ->
                        dependencies.quranText.ayahsOnPage(source.number, script)
                }
            val first = ayahs.firstOrNull()
            val page =
                when (source) {
                    is AyahListSource.Page -> source.number
                    is AyahListSource.Surah ->
                        if (first == null) 1 else dependencies.quranText.pageOf(first, script)
                }
            mutableState.update {
                it.copy(
                    source = source,
                    ayahs = ayahs,
                    blocks = emptyMap(),
                    page = page,
                    juz = JuzTable.juzOfPage(page),
                    headerSurah =
                        chapters.firstOrNull { chapter -> chapter.number == first?.surah },
                    isBookmarked = page in bookmarkedPages,
                )
            }
            if (source is AyahListSource.Page) reportPageOpened(source.number)
        }
    }

    fun openNextPage() = openAdjacentPage(offset = 1)

    fun openPreviousPage() = openAdjacentPage(offset = -1)

    /** Loads the lines of [ayah] unless they are loaded already. */
    fun ensureBlock(ayah: AyahRef) {
        if (mutableState.value.blocks.containsKey(ayah)) return
        viewModelScope.launch {
            val block = loadBlock(ayah)
            mutableState.update { it.copy(blocks = it.blocks + (ayah to block)) }
        }
    }

    fun onAyahTapped(ayah: AyahRef) {
        tappedAyah = ayah
        publishCurrentAyah()
    }

    fun toggleBookmark() {
        val page = mutableState.value.page
        viewModelScope.launch { dependencies.bookmarks.toggleBookmark(page) }
    }

    private fun openAdjacentPage(offset: Int) {
        val source = mutableState.value.source as? AyahListSource.Page ?: return
        val target = source.number + offset
        val pageCount = dependencies.quranText.pageCount(mutableState.value.settings.script)
        if (target in 1..pageCount) openSource(AyahListSource.Page(target))
    }

    private fun ayahsOfSurah(number: Int): List<AyahRef> {
        val versesCount = chapters.firstOrNull { it.number == number }?.versesCount ?: 0
        return (1..versesCount).map { AyahRef(number, it) }
    }

    private suspend fun reportPageOpened(page: Int) {
        dependencies.progressEvents.pageOpened(page)
        dependencies.bookmarks.recordPageOpened(page)
    }

    private suspend fun loadBlock(ayah: AyahRef): AyahBlock {
        val words = dependencies.quranText.wordsOfAyah(ayah)
        val meanings = dependencies.translations.wordByWord(wordLanguage, ayah)
        val names = dependencies.translations.available().associate { it.id to it.name }
        val translations =
            mutableState.value.settings.translations.mapNotNull { id ->
                val passage = dependencies.translations.text(id, ayah) ?: return@mapNotNull null
                val isRange = passage.ayahFrom != passage.ayahTo
                // A merged passage is shown once, at its first ayah (docs/packs.md).
                if (isRange && ayah.ayah != passage.ayahFrom) return@mapNotNull null
                AyahTranslation(
                    name = names[id] ?: id.id,
                    rangeLabel = if (isRange) "${passage.ayahFrom}-${passage.ayahTo}" else null,
                    text = passage.text,
                )
            }
        val transliteration =
            words.mapNotNull { it.transliteration }.joinToString(separator = " ").ifBlank { null }
        return AyahBlock(
            ayah = ayah,
            words =
                words.mapIndexed { index, word ->
                    AyahWord(word.textUthmani, meanings.getOrNull(index))
                },
            transliteration = transliteration,
            translations = translations,
        )
    }

    private suspend fun followSettings() {
        dependencies.settingsStore.settings.distinctUntilChanged().collect { settings ->
            val translationsChanged =
                settings.translations != mutableState.value.settings.translations
            mutableState.update {
                it.copy(
                    settings = settings,
                    blocks = if (translationsChanged) emptyMap() else it.blocks,
                )
            }
        }
    }

    private suspend fun followAudio() {
        dependencies.audioPlayer.state
            .map { dockModelOf(it)?.ayah }
            .distinctUntilChanged()
            .collect {
                playingAyah = it
                publishCurrentAyah()
            }
    }

    private suspend fun followBookmarks() {
        dependencies.bookmarks.bookmarks().collect { bookmarks ->
            bookmarkedPages = bookmarks.map { it.page }.toSet()
            mutableState.update { it.copy(isBookmarked = it.page in bookmarkedPages) }
        }
    }

    private fun publishCurrentAyah() {
        mutableState.update { it.copy(currentAyah = playingAyah ?: tappedAyah) }
    }
}
