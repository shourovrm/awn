package app.awn.quran.feature.read.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.FontPackController
import app.awn.quran.feature.read.page.FontPackStatus
import app.awn.quran.feature.read.page.JuzTable
import app.awn.quran.feature.read.page.PageLoad
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.feature.read.settings.ScreenOrientation
import app.awn.quran.feature.read.settings.TextLayer
import app.awn.quran.feature.read.settings.withTranslation
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.WordProblem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A page counts as read after it has been on screen this long. */
const val PAGE_READ_DELAY_MS = 20_000L

/** The bars show for this long when the screen opens, to teach that they exist. */
const val BARS_REVEAL_MS = 2_000L

/** How a page was reached: by reading on, or by looking something up. */
private enum class Arrival {
    Reading,
    Lookup,
}

/** The things that float over the Quran screen and close first when Back is pressed. */
enum class Popup {
    None,
    TextSize,
    Layers,
    Overflow,
    GoToPage,
}

/** What the Play button of the ayah bar does for the current selection. */
enum class PlayButtonMode {
    /** The selection is not playing: start playing it. */
    Play,
    /** The selection is playing: pause it. */
    Pause,
    /** The selection is paused: carry on from where it stopped. */
    Resume,
}

/** The small card that shows the meaning and transliteration of a tapped word. */
data class WordPopup(
    val ayah: AyahRef,
    val word: AyahWord,
    /** Set when the card opened on a word the user recited wrongly or unclearly. */
    val problem: WordProblem? = null,
    /** True when the chosen reciter has a recording of this word, so the card offers Play. */
    val canPlay: Boolean = false,
)

data class QuranUiState(
    /** False until the first page is known, so the pager opens on the right page at once. */
    val hasStarted: Boolean = false,
    val currentPage: Int = 1,
    val pageCount: Int = DEFAULT_PAGE_COUNT,
    /** Changes on every jump, so the pager moves even when the page number is the same. */
    val jumpToken: Int = 0,
    val settings: ReadSettings = ReadSettings(),
    val layouts: Map<Int, PageLoad> = emptyMap(),
    val contents: Map<Int, PageContent> = emptyMap(),
    val chapters: List<Chapter> = emptyList(),
    val juz: Int = 1,
    val isPageBookmarked: Boolean = false,
    val savedAyahs: Set<AyahRef> = emptySet(),
    val areBarsVisible: Boolean = true,
    /** The first selected ayah; the ayah panel and the Play tab follow it. */
    val selection: AyahRef? = null,
    /** The last ayah of a dragged range; null when only [selection] is selected. */
    val selectionEnd: AyahRef? = null,
    /** The page the selected ayah was chosen on; a swipe to another page clears the selection. */
    val selectionPage: Int? = null,
    val isPanelOpen: Boolean = false,
    val panelBlock: AyahBlock? = null,
    val isReciting: Boolean = false,
    /**
     * The reader moved the page by hand while reciting; the page does not turn for the recitation.
     */
    val isReciteFollowPaused: Boolean = false,
    /** Changes when the pager must animate to [currentPage], as the recitation turns the page. */
    val animatedTurnToken: Int = 0,
    val popup: Popup = Popup.None,
    val wordPopup: WordPopup? = null,
    /** True from the card's Play until the word ends or is stopped; the card shows Stop. */
    val isPopupWordPlaying: Boolean = false,
    val playingAyah: AyahRef? = null,
    /** True while the playing ayah is paused, so the ayah bar offers Resume instead of Pause. */
    val isPlaybackPaused: Boolean = false,
    /** The translations on the phone, for the layers menu of the line-by-line view. */
    val installedTranslations: List<TranslationInfo> = emptyList(),
    /** The page the playing ayah starts on; null while nothing plays. */
    val playingPage: Int? = null,
    /**
     * True while the pager and the lists move with the audio. It turns off when the reader drags
     * the pager or goes to another page, and on again when the reader returns to the playing page,
     * taps the back-to-playing control, or starts a new playback.
     */
    val isFollowing: Boolean = true,
    /** No audio is playing, paused or downloading; only then do the volume keys turn pages. */
    val isAudioIdle: Boolean = true,
    /** Install state of the chosen page type's font; null while unknown. */
    val fontStatus: FontPackStatus? = null,
    val isFontPromptDismissed: Boolean = false,
    /** Changes whenever a pack is installed or removed, so cached fonts are looked up again. */
    val packRevision: Int = 0,
) {
    /** The selected ayahs, or null when nothing is selected. */
    val selectedRange: AyahRange?
        get() = selection?.let { AyahRange(it, selectionEnd ?: it) }

    /** The Play button of the ayah bar: pause or resume when the selected ayahs hold the audio. */
    val playButtonMode: PlayButtonMode
        get() {
            val range = selectedRange
            val isSelectionPlaying = range != null && playingAyah != null && playingAyah in range
            return when {
                !isSelectionPlaying -> PlayButtonMode.Play
                isPlaybackPaused -> PlayButtonMode.Resume
                else -> PlayButtonMode.Pause
            }
        }

    /** The back-to-playing control shows while audio plays and the reader is not following it. */
    val showBackToPlaying: Boolean
        get() = playingAyah != null && !isFollowing

    /** The chosen print's font is missing and the user has not chosen to keep the fallback. */
    val showFontPrompt: Boolean
        get() = fontStatus != null && !fontStatus.isInstalled && !isFontPromptDismissed

    /**
     * The surah the title names for the page shown: the first surah that starts on it, or else the
     * one that continues there. Null until the page is loaded.
     */
    val headerSurah: Chapter?
        get() {
            val layout = (layouts[currentPage] as? PageLoad.Loaded)?.layout
            val number =
                when {
                    layout != null && settings.pageView == PageView.Mushaf ->
                        titleSurahNumber(layout)
                    contents[currentPage] != null ->
                        titleSurahNumber(contents.getValue(currentPage))
                    layout != null -> titleSurahNumber(layout)
                    else -> null
                }
            return chapters.chapterNumbered(number)
        }

    /** True while Back has something to close before the screen may be left. */
    val hasBackWork: Boolean
        get() =
            popup != Popup.None ||
                wordPopup != null ||
                isPanelOpen ||
                selection != null ||
                isReciting ||
                areBarsVisible

    companion object {
        const val DEFAULT_PAGE_COUNT = 604
    }
}

/**
 * Holds the state of the Quran screen: the page, the three views, the bars, the ayah selection and
 * panel, recite mode, playback following and the pages-read timer. Each public function is one user
 * action, so the function count follows the screen, not the logic.
 */
// One class holds the screen's single state, so every action changes it in one place.
@Suppress("TooManyFunctions", "LargeClass")
class QuranViewModel(
    private val dependencies: ReadDependencies,
    /** "en" or "bn": picks the word-by-word pack. */
    wordLanguage: String,
    private val pageReadDelayMs: Long = PAGE_READ_DELAY_MS,
) : ViewModel() {
    private val fontPacks =
        FontPackController(dependencies.packs, dependencies.downloads, viewModelScope)

    private val mutableState = MutableStateFlow(QuranUiState())
    val state: StateFlow<QuranUiState> = mutableState

    /**
     * The word being played. It changes several times a second, so it is kept out of [state]: a
     * page that does not hold the playing ayah must not recompose for it.
     */
    private val mutablePlayingWord = MutableStateFlow<Int?>(null)
    val playingWordPosition: StateFlow<Int?> = mutablePlayingWord

    private val contentLoader =
        PageContentLoader(dependencies.quranText, dependencies.translations, wordLanguage)
    private val wordAudio = WordAudio(dependencies)
    private val shareTexts = AyahShareTexts(contentLoader)

    private var bookmarkedPages: Set<Int> = emptySet()
    private var openedTarget: QuranTarget? = null
    private var hasOpened = false
    private var readTimerJob: Job? = null
    private var barsRevealJob: Job? = null
    private var reciteFollowJob: Job? = null
    private var lastReadPage: Int? = null
    private var isScreenActive = true
    private val resumeTracker =
        ResumeTracker(
            viewModelScope,
            save = { page -> dependencies.bookmarks.recordPageOpened(page) },
        )
    private val pageOfAyahCache = mutableMapOf<AyahRef, Int>()
    private val pagesLoadingContent = mutableSetOf<Int>()

    // The Mushaf page reports a word tap and then the same tap as a page tap. The card of a problem
    // word opens only after its text is read, so without this the page tap would toggle the bars
    // (or close the card it is opening).
    private var problemWordTapPending = false

    init {
        viewModelScope.launch { loadChapters() }
        viewModelScope.launch { loadInstalledTranslations() }
        viewModelScope.launch { followSettings() }
        viewModelScope.launch { followBookmarks() }
        viewModelScope.launch { followSavedAyahs() }
        viewModelScope.launch { followPlayback() }
        viewModelScope.launch { followFontStatus() }
        viewModelScope.launch { followInstalledPacks() }
    }

    /**
     * Opens the screen on [target], or on the page read last when it is null, or on page 1 when
     * nothing was read yet. Opening the same target again, for example after the screen was
     * recreated, does nothing, so the reader is not thrown back.
     */
    fun open(target: QuranTarget?) {
        if (hasOpened && target == openedTarget) return
        hasOpened = true
        openedTarget = target
        viewModelScope.launch {
            // The saved settings come first, so every entry opens in the saved view and print.
            val saved = dependencies.settingsStore.settings.first()
            mutableState.update {
                it.copy(settings = saved, fontStatus = fontPacks.statuses.value[saved.script])
            }
            when (target) {
                is QuranTarget.Page -> showPage(target.page, barsVisible = true)
                is QuranTarget.Ayah -> {
                    val page = dependencies.quranText.pageOf(target.ayah, scriptNow())
                    showPage(page, barsVisible = true)
                    selectAyah(target.ayah)
                }
                null -> {
                    val lastPage = dependencies.bookmarks.recentPages().first().firstOrNull()?.page
                    showPage(lastPage ?: FIRST_PAGE, barsVisible = true)
                }
            }
            revealBarsBriefly()
        }
    }

    /**
     * Turns to the page of [word], the next word the reciter is expected to say, so the words being
     * recited stay on screen. A turn to the neighbouring page is animated like a swipe; a longer
     * way jumps. A reader who moved the page by hand is left alone (see [decideReciteFollow]).
     */
    fun followRecitation(word: WordKey) {
        reciteFollowJob?.cancel()
        reciteFollowJob = viewModelScope.launch {
            val wordPage = pageOfWord(word)
            val state = mutableState.value
            if (!state.isReciting || !state.hasStarted) return@launch
            val decision =
                decideReciteFollow(wordPage, state.currentPage, state.isReciteFollowPaused)
            mutableState.update { it.copy(isReciteFollowPaused = decision.isPaused) }
            val target = decision.turnToPage ?: return@launch
            if (isSinglePageTurn(state.currentPage, target)) {
                showPage(
                    target,
                    barsVisible = state.areBarsVisible,
                    movesPager = false,
                    arrival = Arrival.Reading,
                )
                mutableState.update { it.copy(animatedTurnToken = it.animatedTurnToken + 1) }
            } else {
                showPage(target, barsVisible = state.areBarsVisible, movesPager = true)
            }
        }
    }

    /**
     * Moves the pager to [page] at once, in every view. Used by Go to page and the index. This is a
     * lookup: it becomes the resume point only if the reader stays.
     */
    fun jumpToPage(page: Int) {
        pauseReciteFollow()
        showPage(page, barsVisible = mutableState.value.areBarsVisible, movesPager = true)
    }

    /** Turns the page by [step] pages, as the volume keys do. Turning pages is reading. */
    fun turnPage(step: Int) {
        pauseReciteFollow()
        val state = mutableState.value
        showPage(
            state.currentPage + step,
            barsVisible = state.areBarsVisible,
            movesPager = true,
            arrival = Arrival.Reading,
        )
    }

    /** Called when the pager has come to rest on [page]; the pager is already there. */
    fun onPageSettled(page: Int) {
        val state = mutableState.value
        val validPage = page.coerceIn(FIRST_PAGE, state.pageCount)
        if (state.hasStarted && validPage == state.currentPage) {
            // A drag that came back to the playing page follows the audio again.
            if (validPage == state.playingPage && !state.isFollowing) {
                mutableState.update { it.copy(isFollowing = true) }
            }
            return
        }
        showPage(
            validPage,
            barsVisible = state.areBarsVisible,
            movesPager = false,
            arrival = Arrival.Reading,
        )
    }

    /** Starts loading the page, in the form the chosen view needs, unless it is loaded. */
    fun loadPage(page: Int) {
        val state = mutableState.value
        if (page !in FIRST_PAGE..state.pageCount) return
        if (state.settings.pageView == PageView.Mushaf) {
            loadLayout(page)
        } else {
            loadContent(page)
        }
    }

    /** The view button: Mushaf or line by line. The choice is saved. */
    fun switchView() {
        updateSettings { it.copy(pageView = it.pageView.other()) }
    }

    /** A layers-menu tickbox for Arabic, transliteration or word by word. */
    fun toggleLayer(layer: TextLayer) {
        updateSettings { settings ->
            val changed =
                when (layer) {
                    TextLayer.Arabic -> settings.copy(showArabic = !settings.showArabic)
                    TextLayer.Transliteration ->
                        settings.copy(showTransliteration = !settings.showTransliteration)
                    TextLayer.WordByWord -> settings.copy(showWordByWord = !settings.showWordByWord)
                    TextLayer.Translation -> settings
                }
            keepingOneLayer(settings, changed)
        }
    }

    /** A layers-menu tickbox for one translation. */
    fun toggleTranslation(id: TranslationId) {
        updateSettings { settings ->
            val changed =
                settings.copy(
                    translations =
                        settings.translations.withTranslation(id, id !in settings.translations),
                )
            keepingOneLayer(settings, changed)
        }
    }

    /** The text-size popup: moves the size of one layer by [delta] steps. */
    fun changeTextSize(layer: TextLayer, delta: Int) {
        updateSettings { it.withStepMoved(layer, delta) }
    }

    /** The text-size popup in the Mushaf view: prints the page larger or smaller. */
    fun changeMushafSize(delta: Int) {
        updateSettings { it.withMushafStepMoved(delta) }
    }

    /** The rotate button: turns the Quran pages to the other way than they are now. */
    fun rotate(isLandscapeNow: Boolean) {
        val next = if (isLandscapeNow) ScreenOrientation.Portrait else ScreenOrientation.Landscape
        updateSettings { it.copy(quranOrientation = next) }
    }

    /** An empty page helps nobody, so a tick that would hide the last layer is ignored. */
    private fun keepingOneLayer(before: ReadSettings, after: ReadSettings): ReadSettings =
        if (after.visibleLayers.isEmpty()) before else after

    private fun updateSettings(transform: (ReadSettings) -> ReadSettings) {
        viewModelScope.launch { dependencies.settingsStore.update(transform) }
    }

    /** The reader dragged the pager: stop turning pages for the audio until they come back. */
    fun onPagerDragged() {
        pauseReciteFollow()
        mutableState.update { it.copy(isFollowing = false) }
    }

    /**
     * The reader moved the page by hand while reciting: the page stops turning until they return.
     */
    private fun pauseReciteFollow() {
        mutableState.update { if (it.isReciting) it.copy(isReciteFollowPaused = true) else it }
    }

    /** The back-to-playing control: follow the audio again and show the playing ayah. */
    fun backToPlaying() {
        val state = mutableState.value
        val page = state.playingPage ?: return
        mutableState.update { it.copy(isFollowing = true) }
        if (page != state.currentPage) {
            showPage(page, barsVisible = state.areBarsVisible)
        } else {
            // The list views scroll to the ayah again; the page has nothing to move.
            mutableState.update { it.copy(jumpToken = it.jumpToken + 1) }
        }
    }

    /**
     * A tap on the page. Whatever floats over it (the word card, the ayah bar) closes first and the
     * bars stay as they are; with nothing open the tap shows or hides the bars. The ayah panel has
     * its own close button, so a tap above it still toggles the bars.
     */
    fun onPageTapped() {
        if (problemWordTapPending) {
            problemWordTapPending = false
            return
        }
        val state = mutableState.value
        when {
            state.wordPopup != null -> dismissWordPopup()
            state.selection != null && !state.isPanelOpen -> clearSelection()
            else -> toggleBars()
        }
    }

    fun toggleBars() {
        // A bar the reader shows or hides by hand stays as they left it.
        barsRevealJob?.cancel()
        mutableState.update { it.copy(areBarsVisible = !it.areBarsVisible) }
    }

    /** Shows the bars when the screen opens and hides them after [BARS_REVEAL_MS]. */
    private fun revealBarsBriefly() {
        barsRevealJob?.cancel()
        barsRevealJob = viewModelScope.launch {
            delay(BARS_REVEAL_MS)
            mutableState.update { it.copy(areBarsVisible = false) }
        }
    }

    fun openPopup(popup: Popup) {
        mutableState.update { it.copy(popup = popup) }
    }

    fun closePopup() {
        mutableState.update { it.copy(popup = Popup.None) }
    }

    fun toggleBookmarkOfPage() {
        val page = mutableState.value.currentPage
        viewModelScope.launch { dependencies.bookmarks.toggleBookmark(page) }
    }

    /** Selects [ayah] on the page shown; selecting another ayah moves the selection and panel. */
    fun selectAyah(ayah: AyahRef) {
        mutableState.update {
            it.copy(
                selection = ayah,
                selectionEnd = null,
                selectionPage = it.currentPage,
                wordPopup = null,
            )
        }
        if (mutableState.value.isPanelOpen) loadPanelBlock(ayah)
    }

    /** Selects the ayahs of a long press and drag; the panel and Play tab follow the first. */
    fun selectRange(range: AyahRange) {
        mutableState.update {
            it.copy(
                selection = range.first,
                selectionEnd = range.last.takeUnless { last -> last == range.first },
                selectionPage = it.currentPage,
                wordPopup = null,
            )
        }
        if (mutableState.value.isPanelOpen) loadPanelBlock(range.first)
    }

    fun clearSelection() {
        mutableState.update {
            it.copy(
                selection = null,
                selectionEnd = null,
                selectionPage = null,
                isPanelOpen = false,
                panelBlock = null,
            )
        }
    }

    /** Opens the ayah panel for the selected ayah. */
    fun openPanel() {
        val ayah = mutableState.value.selection ?: return
        mutableState.update { it.copy(isPanelOpen = true) }
        loadPanelBlock(ayah)
    }

    fun closePanel() {
        mutableState.update { it.copy(isPanelOpen = false, panelBlock = null) }
    }

    /**
     * Moves the selection to the next ([delta] = 1) or previous ([delta] = -1) ayah. The pager
     * follows when that ayah is on another page.
     */
    fun selectAdjacentAyah(delta: Int) {
        val state = mutableState.value
        val current = state.selection ?: return
        val target = adjacentAyah(current, delta, state.chapters) ?: return
        val wasPanelOpen = state.isPanelOpen
        viewModelScope.launch {
            val page = pageOfAyah(target)
            if (page != mutableState.value.currentPage) {
                showPage(page, barsVisible = mutableState.value.areBarsVisible)
            }
            selectAyah(target)
            // Showing another page closes the panel; the panel follows the selection instead.
            if (wasPanelOpen) openPanel()
        }
    }

    /**
     * The Play button of the ayah bar. While the selected ayahs are playing it pauses them, while
     * they are paused it resumes, otherwise [startPlayback] starts them. Starting or resuming
     * closes the bar so it does not hang over the page; the playing fill stays.
     */
    fun onPlayPressed(startPlayback: () -> Unit) {
        when (mutableState.value.playButtonMode) {
            PlayButtonMode.Pause -> dependencies.audioPlayer.pause()
            PlayButtonMode.Resume -> {
                dependencies.audioPlayer.resume()
                clearSelection()
            }
            PlayButtonMode.Play -> {
                startPlayback()
                clearSelection()
            }
        }
    }

    /** Switches the bottom bar to the recite bar. The caller also tells :feature:recite to load. */
    fun enterReciteMode() {
        mutableState.update {
            it.copy(
                isReciting = true,
                isReciteFollowPaused = false,
                selection = null,
                selectionEnd = null,
                selectionPage = null,
                isPanelOpen = false,
                panelBlock = null,
            )
        }
    }

    fun exitReciteMode() {
        mutableState.update { it.copy(isReciting = false) }
    }

    /** Saves [ayah] as a bookmark, or removes it when it is saved. */
    fun toggleSavedAyah(ayah: AyahRef) {
        viewModelScope.launch {
            if (ayah in mutableState.value.savedAyahs) {
                dependencies.bookmarks.removeSavedAyah(ayah)
            } else {
                dependencies.bookmarks.saveAyah(ayah, note = null)
            }
        }
    }

    /**
     * Opens the card with the word's meaning. A tap never makes a sound: the card has its own play
     * button, shown when the chosen reciter has word timings, so reading never starts audio (and,
     * while the microphone listens, the reciter is never heard by accident).
     */
    fun onWordTapped(ayah: AyahRef, word: AyahWord, problem: WordProblem? = null) {
        // The word card and the ayah bar never show together.
        mutableState.update {
            it.copy(
                wordPopup = WordPopup(ayah, word, problem),
                isPopupWordPlaying = false,
                selection = null,
                selectionEnd = null,
                selectionPage = null,
                isPanelOpen = false,
                panelBlock = null,
            )
        }
        viewModelScope.launch { showPlayWhen(wordAudio.hasTimings(ayah), word.key) }
    }

    /**
     * A tap on a word of the Mushaf page that was recited wrongly or unclearly: the page knows only
     * the key, so the word's text, meaning and transliteration are read before the card opens.
     */
    fun onProblemWordTapped(key: WordKey, problem: WordProblem) {
        problemWordTapPending = true
        viewModelScope.launch {
            val ayah = AyahRef(key.surah, key.ayah)
            val block =
                contentLoader.loadBlock(ayah, enabledTranslations = emptyList(), names = emptyMap())
            val word = block.words.firstOrNull { it.key == key } ?: return@launch
            onWordTapped(ayah, word, problem)
        }
    }

    /**
     * Plays the reciter's recording of the word on the open card; the card shows Stop meanwhile.
     */
    fun playPopupWord() {
        val popup = mutableState.value.wordPopup ?: return
        mutableState.update { it.copy(isPopupWordPlaying = true) }
        viewModelScope.launch { wordAudio.play(popup.ayah, popup.word.key.wordPosition) }
    }

    /** Stops the word that the open card is playing. */
    fun stopPopupWord() {
        mutableState.update { it.copy(isPopupWordPlaying = false) }
        dependencies.audioPlayer.stop()
    }

    // The card may have closed, or moved to another word, while the timings were read.
    private fun showPlayWhen(hasTimings: Boolean, key: WordKey) {
        mutableState.update { state ->
            val popup = state.wordPopup
            if (popup != null && popup.word.key == key) {
                state.copy(wordPopup = popup.copy(canPlay = hasTimings))
            } else {
                state
            }
        }
    }

    fun dismissWordPopup() {
        mutableState.update { it.copy(wordPopup = null, isPopupWordPlaying = false) }
    }

    /** The copy and share text of [range] with the first enabled translation. */
    suspend fun shareTextOf(range: AyahRange): String {
        val state = mutableState.value
        return shareTexts.of(range, state.settings.translations.take(1), state.chapters)
    }

    /**
     * Closes the innermost open thing and returns true, or returns false when nothing is open and
     * the screen may be left. The order is: popups, ayah panel, ayah selection, recite mode, bars.
     * The saved view is never touched.
     */
    fun onBack(): Boolean {
        val state = mutableState.value
        when {
            state.popup != Popup.None -> closePopup()
            state.wordPopup != null -> dismissWordPopup()
            state.isPanelOpen -> closePanel()
            state.selection != null -> clearSelection()
            state.isReciting -> exitReciteMode()
            state.areBarsVisible -> {
                barsRevealJob?.cancel()
                mutableState.update { it.copy(areBarsVisible = false) }
            }
            else -> return false
        }
        return true
    }

    /** Tells the pages-read timer whether the screen is in front; it counts only then. */
    fun setScreenActive(isActive: Boolean) {
        isScreenActive = isActive
        if (isActive) resumeTracker.onStart() else resumeTracker.onStop()
        restartReadTimer()
    }

    /**
     * Makes [page] the page shown. [movesPager] is true for jumps, which bump
     * [QuranUiState.jumpToken] so the pager scrolls there; a swipe has already moved the pager.
     * [arrival] decides how the resume point follows (see [ResumeTracker]).
     */
    private fun showPage(
        page: Int,
        barsVisible: Boolean,
        movesPager: Boolean = true,
        arrival: Arrival = Arrival.Lookup,
    ) {
        val pageCount = mutableState.value.pageCount
        val validPage = page.coerceIn(FIRST_PAGE, pageCount)
        mutableState.update {
            val keepsSelection = it.selection != null && it.selectionPage == validPage
            it.copy(
                hasStarted = true,
                currentPage = validPage,
                jumpToken = if (movesPager) it.jumpToken + 1 else it.jumpToken,
                juz = JuzTable.juzOfPage(validPage),
                areBarsVisible = barsVisible,
                selection = if (keepsSelection) it.selection else null,
                selectionEnd = if (keepsSelection) it.selectionEnd else null,
                selectionPage = if (keepsSelection) it.selectionPage else null,
                isPanelOpen = it.isPanelOpen && keepsSelection,
                panelBlock = if (keepsSelection) it.panelBlock else null,
                popup = Popup.None,
                wordPopup = null,
                isPageBookmarked = validPage in bookmarkedPages,
                isFollowing = isFollowingOn(it.isFollowing, it.playingPage, validPage),
            )
        }
        loadAroundPage(validPage)
        when (arrival) {
            Arrival.Reading -> resumeTracker.onReading(validPage)
            Arrival.Lookup -> resumeTracker.onLookup(validPage)
        }
        restartReadTimer()
    }

    /** The page and its neighbours, so a swipe either way shows a page at once. */
    private fun loadAroundPage(page: Int) {
        loadPage(page)
        loadPage(page - 1)
        loadPage(page + 1)
    }

    private fun restartReadTimer() {
        readTimerJob?.cancel()
        val state = mutableState.value
        if (!isScreenActive || !state.hasStarted) return
        val page = state.currentPage
        readTimerJob = viewModelScope.launch {
            delay(pageReadDelayMs)
            reportPageRead(page)
        }
    }

    /** Learn counts pages read from this event, so it is sent only for a page that was read. */
    private suspend fun reportPageRead(page: Int) {
        if (lastReadPage == page) return
        lastReadPage = page
        dependencies.progressEvents.pageOpened(page)
    }

    private fun loadLayout(page: Int) {
        val state = mutableState.value
        if (state.layouts[page] != null) return
        val remembered = RecentLayouts.get(dependencies.quranText, state.settings.script, page)
        if (remembered != null) {
            mutableState.update {
                it.copy(layouts = it.layouts + (page to PageLoad.Loaded(remembered)))
            }
            return
        }
        mutableState.update { it.copy(layouts = it.layouts + (page to PageLoad.Loading)) }
        viewModelScope.launch {
            val load = fetchLayout(page, state.settings.script)
            mutableState.update { current ->
                current.copy(layouts = current.layouts + (page to load))
            }
        }
    }

    private fun loadContent(page: Int) {
        val state = mutableState.value
        if (state.contents[page] != null || !pagesLoadingContent.add(page)) return
        viewModelScope.launch {
            val current = mutableState.value
            val content =
                contentLoader.load(
                    page = page,
                    script = current.settings.script,
                    enabledTranslations = current.settings.translations,
                    chapters = current.chapters,
                )
            pagesLoadingContent.remove(page)
            mutableState.update { latest ->
                latest.copy(contents = latest.contents + (page to content))
            }
        }
    }

    private fun loadPanelBlock(ayah: AyahRef) {
        viewModelScope.launch {
            val block = contentLoader.loadBlock(ayah, mutableState.value.settings.translations)
            mutableState.update {
                // The reader may have moved to another ayah while this one loaded.
                if (it.selection == ayah && it.isPanelOpen) it.copy(panelBlock = block) else it
            }
        }
    }

    private suspend fun loadChapters() {
        val chapters = dependencies.quranText.chapters()
        mutableState.update { it.copy(chapters = chapters) }
        // List views build their surah bands from the chapters, so pages loaded before them redo.
        if (mutableState.value.hasStarted) {
            mutableState.update { it.copy(contents = emptyMap()) }
            loadPage(mutableState.value.currentPage)
        }
    }

    private suspend fun loadInstalledTranslations() {
        val installed = dependencies.translations.available().filter { it.installed }
        mutableState.update { it.copy(installedTranslations = layerTranslations(installed)) }
    }

    private suspend fun followSettings() {
        dependencies.settingsStore.settings.distinctUntilChanged().collect { settings ->
            val previous = mutableState.value.settings
            val scriptChanged = settings.script != previous.script
            val translationsChanged = settings.translations != previous.translations
            val viewChanged = settings.pageView != previous.pageView
            mutableState.update {
                it.copy(
                    settings = settings,
                    isFontPromptDismissed = if (scriptChanged) false else it.isFontPromptDismissed,
                    fontStatus =
                        if (scriptChanged) fontPacks.statuses.value[settings.script]
                        else it.fontStatus,
                    layouts = if (scriptChanged) emptyMap() else it.layouts,
                    contents =
                        if (scriptChanged || translationsChanged) emptyMap() else it.contents,
                )
            }
            val needsReload = scriptChanged || translationsChanged || viewChanged
            if (mutableState.value.hasStarted && needsReload) {
                loadAroundPage(mutableState.value.currentPage)
            }
        }
    }

    /** Downloads the font of the chosen page type; progress and failure show in the state. */
    fun downloadFont() {
        fontPacks.install(mutableState.value.settings.script)
    }

    /** Stops the running font download; the prompt offers Download again. */
    fun cancelFont() {
        fontPacks.cancel(mutableState.value.settings.script)
    }

    /** Keeps the fallback font for the chosen page type and hides the prompt. */
    fun keepFallbackFont() {
        mutableState.update { it.copy(isFontPromptDismissed = true) }
    }

    private suspend fun followFontStatus() {
        fontPacks.statuses.collect { statuses ->
            mutableState.update { it.copy(fontStatus = statuses[it.settings.script]) }
        }
    }

    /**
     * A newly installed print must replace the fallback already on screen: the layouts loaded with
     * the fallback are dropped and the visible pages load again with the new font.
     */
    private suspend fun followInstalledPacks() {
        dependencies.packs.installed().drop(1).collect {
            RecentLayouts.clear()
            loadInstalledTranslations()
            mutableState.update {
                // The page lists hold translation text, so a new or removed translation redoes
                // them.
                it.copy(
                    packRevision = it.packRevision + 1,
                    layouts = emptyMap(),
                    contents = emptyMap(),
                )
            }
            if (mutableState.value.hasStarted) {
                loadAroundPage(mutableState.value.currentPage)
            }
        }
    }

    private suspend fun followBookmarks() {
        dependencies.bookmarks
            .bookmarks()
            .map { bookmarks -> bookmarks.map { it.page }.toSet() }
            .collect { pages ->
                bookmarkedPages = pages
                mutableState.update { it.copy(isPageBookmarked = it.currentPage in pages) }
            }
    }

    private suspend fun followSavedAyahs() {
        dependencies.bookmarks.savedAyahs().collect { saved ->
            mutableState.update { it.copy(savedAyahs = saved.map { entry -> entry.ayah }.toSet()) }
        }
    }

    private suspend fun followPlayback() {
        dependencies.audioPlayer.state.distinctUntilChanged().collect { playback ->
            val ayah = playback.heardAyah()
            val wordPosition = (playback as? PlaybackState.Playing)?.wordPosition
            val previousAyah = mutableState.value.playingAyah
            val playingPage = ayah?.let { pageOfAyah(it) }
            mutablePlayingWord.value = wordPosition
            val hasStarted = previousAyah == null && ayah != null
            mutableState.update {
                it.copy(
                    playingAyah = ayah,
                    isPlaybackPaused = playback is PlaybackState.Paused,
                    playingPage = playingPage,
                    isAudioIdle = playback is PlaybackState.Idle || playback is PlaybackState.Error,
                    // The word on the card has ended once the player is idle again.
                    isPopupWordPlaying =
                        it.isPopupWordPlaying &&
                            playback !is PlaybackState.Idle &&
                            playback !is PlaybackState.Error,
                    // A new playback is followed from its first ayah, wherever the reader is.
                    isFollowing = if (hasStarted) true else it.isFollowing,
                )
            }
            if (ayah != null && ayah != previousAyah) followPlayingAyah(playingPage)
        }
    }

    /** Turns the pager to the playing ayah's page while the reader is following the audio. */
    private fun followPlayingAyah(playingPage: Int?) {
        val state = mutableState.value
        if (
            playingPage != null &&
                state.hasStarted &&
                shouldTurnToPlayingPage(state.isFollowing, playingPage, state.currentPage)
        ) {
            showPage(playingPage, barsVisible = state.areBarsVisible)
        }
    }

    /**
     * The page that holds [word]. An ayah that runs over a page break starts on one page and ends
     * on the next, so the layouts from the ayah's first page on are searched; a print that is not
     * installed or has no such word gives the ayah's first page.
     */
    private suspend fun pageOfWord(word: WordKey): Int {
        val firstPage = pageOfAyah(AyahRef(word.surah, word.ayah))
        val script = scriptNow()
        for (page in
            firstPage until
                minOf(firstPage + MAX_PAGES_OF_AYAH, mutableState.value.pageCount + 1)) {
            val layout = layoutOrNull(page, script) ?: layoutOrNull(page, ScriptId.QpcHafs)
            if (layout != null && layout.holds(word)) return page
        }
        return firstPage
    }

    private suspend fun pageOfAyah(ayah: AyahRef): Int =
        pageOfAyahCache.getOrPut(ayah) { dependencies.quranText.pageOf(ayah, scriptNow()) }

    private fun scriptNow(): ScriptId = mutableState.value.settings.script

    /**
     * Reads [page] in [script]; when that print is not installed the Unicode Hafs layout stands in,
     * so the reader always has something to read.
     */
    private suspend fun fetchLayout(page: Int, script: ScriptId): PageLoad {
        val layout =
            layoutOrNull(page, script)
                ?: if (script == ScriptId.QpcHafs) null else layoutOrNull(page, ScriptId.QpcHafs)
        // The fallback layout is for a print that is not installed; it is not kept under the
        // name of the print the reader asked for.
        if (layout != null && layout.script == script) {
            RecentLayouts.put(dependencies.quranText, layout)
        }
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
        const val FIRST_PAGE = 1

        /** The longest ayah (2:282) fills a page and a half; three pages cover any ayah. */
        const val MAX_PAGES_OF_AYAH = 3
    }
}

private fun PageLayout.holds(word: WordKey): Boolean = lines.any { line ->
    line.items.any { item -> item is LineItem.WordItem && item.word.key == word }
}

/** The ayah the reader hears or has paused on; nothing is heard while the audio downloads. */
private fun PlaybackState.heardAyah(): AyahRef? =
    when (this) {
        is PlaybackState.Playing -> ayah
        is PlaybackState.Paused -> ayah
        is PlaybackState.Idle,
        is PlaybackState.Downloading,
        is PlaybackState.Error -> null
    }
