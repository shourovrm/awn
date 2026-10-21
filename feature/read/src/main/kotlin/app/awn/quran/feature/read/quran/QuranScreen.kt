@file:Suppress(
    "MatchingDeclarationName",
) // QuranScreenSlots is the contract; the screen follows it.

package app.awn.quran.feature.read.quran

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.FontLookup
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.PageLoad
import app.awn.quran.feature.read.page.SelectionBounds
import app.awn.quran.feature.read.page.SharedPageFontCache
import app.awn.quran.feature.read.page.isGlyphScript
import app.awn.quran.feature.read.page.rememberFontLookup
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.launch

/**
 * What the Quran screen cannot own itself, supplied by :app from the other feature modules. The
 * shape is fixed by docs/redesign.md.
 */
data class QuranScreenSlots(
    val audioBar: @Composable (page: Int) -> Unit,
    val playPanel: @Composable (ayah: AyahRef, page: Int) -> Unit,
    val onPlayFrom: (AyahRef) -> Unit,
    val reciteBar: @Composable (onExit: () -> Unit) -> Unit,
    val onReciteFrom: (AyahRef) -> Unit,
    val reciteWordStates: @Composable () -> Map<WordKey, WordState>,
    /**
     * Optional. What went wrong with each red or yellow word while reciting; a tap on such a word
     * opens a card with it.
     */
    val reciteWordProblems: @Composable () -> Map<WordKey, WordProblem> = { emptyMap() },
    val isNight: Boolean,
    val onToggleNight: () -> Unit,
    val onOpenIndex: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenLearn: () -> Unit,
    val onOpenSettings: () -> Unit,
    /**
     * Optional. Plays the ayahs from the first to the last as one range ("Play from here" after a
     * long press and drag). When null, the bar calls [onPlayFrom] with the first ayah.
     */
    val onPlayRange: ((first: AyahRef, last: AyahRef) -> Unit)? = null,
    /**
     * Optional. The Play tab for a dragged range: the same panel as [playPanel], told where the
     * range ends. When null, [playPanel] gets the first ayah only. [playPanel] still gets the first
     * ayah when only one ayah is selected.
     */
    val playPanelRange: (@Composable (first: AyahRef, last: AyahRef, page: Int) -> Unit)? = null,
    /**
     * Optional. True while reciting was started outside the screen (revision from Learn), so the
     * screen shows the recite bar without a long press.
     */
    val isReciteActive: Boolean = false,
    /** Optional. The ayah the reciter is on; the page turns to it as the recitation moves on. */
    val reciteCurrentAyah: @Composable () -> AyahRef? = { null },
    /** Optional. The next word the reciter is expected to say; the pager turns to its page. */
    val reciteCurrentWord: @Composable () -> WordKey? = { null },
    /**
     * Optional. The flagged word the reciter is reviewing in the review card; the page turns to it
     * and rings it.
     */
    val reciteFocusedWord: @Composable () -> WordKey? = { null },
    /** Optional. A tap on a flagged word while reciting asks the review card to open on it. */
    val onReciteFocusWord: (WordKey) -> Unit = {},
    /**
     * Optional. Called when Back is pressed and nothing on the screen is open; the host goes to its
     * home page. When null, Back is left to the host's default (leaving the app).
     */
    val onBackWhenIdle: (() -> Unit)? = null,
) {
    companion object {
        /** Slots that draw nothing and do nothing, for hosts that wire the parts later. */
        val Empty =
            QuranScreenSlots(
                audioBar = {},
                playPanel = { _, _ -> },
                onPlayFrom = {},
                reciteBar = {},
                onReciteFrom = {},
                reciteWordStates = { emptyMap() },
                isNight = false,
                onToggleNight = {},
                onOpenIndex = {},
                onOpenSearch = {},
                onOpenLearn = {},
                onOpenSettings = {},
            )
    }
}

private const val PANEL_SHARE = 0.6f

/** Bars fade in and out in this long: the first change on screen comes well inside 150 ms. */
internal const val BAR_FADE_MS = 120
private const val FIRST_PAGE = 1

/**
 * The Quran screen: a pager of the 604 pages in one of three views, with a top bar, the audio or
 * recite bar, the ayah selection bar and the ayah panel over it. [target] opens a page, or an ayah
 * that is selected; null opens the page read last. Search results and the index pass a target.
 *
 * Back closes popups, the ayah panel, the selection, recite mode and the bars, in that order, and
 * is left to the host (`onBackWhenIdle`) only when none is open.
 */
@Composable
fun QuranScreen(
    dependencies: ReadDependencies,
    slots: QuranScreenSlots,
    modifier: Modifier = Modifier,
    target: QuranTarget? = null,
) {
    // The activity's locale is the app language set in Settings; the view model outlives the
    // recreation that follows a language change, so it is told again instead of reading it once.
    val appLanguage = LocalConfiguration.current.locales[0].language
    val viewModel: QuranViewModel =
        viewModel(
            factory =
                viewModelFactory { initializer { QuranViewModel(dependencies, appLanguage) } },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(appLanguage) { viewModel.onAppLanguageChanged(appLanguage) }
    LaunchedEffect(target) { viewModel.open(target) }
    FollowRecitation(slots, viewModel, isReciting = state.isReciting)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.setScreenActive(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.setScreenActive(false) }
    BackHandler(enabled = state.hasBackWork || slots.onBackWhenIdle != null) {
        if (!viewModel.onBack()) slots.onBackWhenIdle?.invoke()
    }
    QuranOrientation(
        quranOrientation = state.settings.quranOrientation,
        appOrientation = state.settings.appOrientation,
    )

    val focusRequester = remember { FocusRequester() }
    val turnsPagesWithVolume = state.settings.volumeKeysTurnPages
    if (turnsPagesWithVolume) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val keyModifier =
        if (turnsPagesWithVolume) {
            Modifier.focusRequester(focusRequester).focusable().onPreviewKeyEvent { event ->
                val isVolumeKey = event.key == Key.VolumeDown || event.key == Key.VolumeUp
                if (!isVolumeKey) return@onPreviewKeyEvent false
                // While audio is active the keys change the volume, as the system does.
                val action =
                    volumeKeyAction(
                        isVolumeDown = event.key == Key.VolumeDown,
                        isAudioIdle = state.isAudioIdle,
                    ) ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    viewModel.turnPage(
                        when (action) {
                            VolumeKeyAction.NextPage -> 1
                            VolumeKeyAction.PreviousPage -> -1
                        },
                    )
                }
                true
            }
        } else {
            Modifier
        }

    CompositionLocalProvider(LocalViewConfiguration provides rememberFixedLongPress()) {
        Box(modifier.fillMaxSize().background(Awn.field.background).then(keyModifier)) {
            if (state.hasStarted) {
                QuranContent(dependencies, slots, viewModel, state)
            }
        }
    }
}

// One layered screen: pager, bars, selection bar, panel, word popup and dialog.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun QuranContent(
    dependencies: ReadDependencies,
    slots: QuranScreenSlots,
    viewModel: QuranViewModel,
    state: QuranUiState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Not read here: only the page that holds the playing ayah reads the word position.
    val playingWord = viewModel.playingWordPosition.collectAsStateWithLifecycle()
    val fontCache =
        remember(dependencies.packs, state.packRevision) {
            SharedPageFontCache.obtain(dependencies.packs, state.packRevision)
        }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var listAnchor by remember { mutableStateOf<Rect?>(null) }
    // Where the selected ayahs are on the Mushaf page, in the page's own (unshifted) coordinates.
    var mushafBounds by remember { mutableStateOf<SelectionBounds?>(null) }
    // True from the long press until the finger lifts; the bar waits for the release.
    var isSelecting by remember { mutableStateOf(false) }
    // Where the tapped word is, in window coordinates, for the word card.
    var wordAnchor by remember { mutableStateOf<Rect?>(null) }
    // Where the word the reciter is on lies on the Mushaf page, in the page's own coordinates.
    var trackedWordBounds by remember { mutableStateOf<Rect?>(null) }
    // The height of everything stacked at the bottom while reciting; the lists end above it.
    var bottomStackHeightPx by remember { mutableIntStateOf(0) }
    // Remembered, so the pages see the same objects after every state change and can skip.
    val selectionActions = remember {
        MushafSelectionActions(
            onRangeStart = { isSelecting = true },
            onRangeEnd = { isSelecting = false },
            onBounds = { mushafBounds = it },
        )
    }
    val onListAnchor: (Rect?) -> Unit = remember { { anchor -> listAnchor = anchor } }
    val onTrackedWordBounds: (Rect?) -> Unit = remember { { bounds -> trackedWordBounds = bounds } }

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInWindow() },
    ) {
        val fullHeight = maxHeight
        val fullHeightPx = with(density) { fullHeight.roundToPx() }
        val panelHeight = fullHeight * PANEL_SHARE
        // The panel appears at once: Back must feel instant.
        val windowHeight = if (state.isPanelOpen) fullHeight - panelHeight else fullHeight
        val windowHeightPx = with(density) { windowHeight.roundToPx() }

        val marginPx = with(density) { AwnSpacing.medium.toPx() }
        val isMushaf = state.settings.pageView == PageView.Mushaf
        val pageBounds = mushafBounds?.translate(Offset(marginPx, 0f))
        val panelShiftPx =
            if (state.isPanelOpen && isMushaf && pageBounds != null) {
                // The page keeps its full height under the panel and slides up only as far as
                // the selected ayahs need, never further than the panel is tall.
                scrollToKeepVisible(
                        pageBounds.all.top,
                        pageBounds.all.bottom,
                        windowHeightPx.toFloat(),
                        marginPx,
                    )
                    .coerceAtMost((fullHeightPx - windowHeightPx).toFloat())
                    .toInt()
            } else {
                0
            }
        // While the audio bar shows, a selection on the last lines would sit under it: the page
        // slides up just enough, then back when the bar hides.
        val audioBarClearancePx =
            with(density) { (AwnSpacing.dockHeight + AwnSpacing.small).toPx() }
        val isAudioBarOverPage = state.areBarsVisible && !state.isPanelOpen
        // While reciting, the recite bar is two or three rows tall and the reciter moves down the
        // page, so the page slides up to keep the word being recited above the bar.
        val reciteWordBounds = trackedWordBounds
        val barShiftTarget =
            when {
                state.isReciting &&
                    isMushaf &&
                    reciteWordBounds != null &&
                    bottomStackHeightPx > 0 ->
                    shiftToClearAudioBar(
                            spanTop = reciteWordBounds.top,
                            spanBottom = reciteWordBounds.bottom,
                            pageHeight = fullHeightPx.toFloat(),
                            clearance = bottomStackHeightPx.toFloat(),
                            margin = marginPx,
                        )
                        .toInt()
                isAudioBarOverPage && isMushaf && pageBounds != null ->
                    shiftToClearAudioBar(
                            spanTop = pageBounds.all.top,
                            spanBottom = pageBounds.all.bottom,
                            pageHeight = fullHeightPx.toFloat(),
                            clearance = audioBarClearancePx,
                            margin = marginPx,
                        )
                        .toInt()
                else -> 0
            }
        // The animated part is read only when the page is placed, so the 120 ms slide does not
        // recompose the pager on every frame.
        val barShiftPx = animateIntAsState(barShiftTarget, tween(BAR_FADE_MS))
        val currentShiftPx: () -> Int =
            remember(panelShiftPx, barShiftPx) { { panelShiftPx + barShiftPx.value } }

        val focusedWord = if (state.isReciting) slots.reciteFocusedWord() else null
        val reciteMarks =
            if (state.isReciting) {
                ReciteMarks(
                    states = slots.reciteWordStates(),
                    problems = slots.reciteWordProblems(),
                    // A mistake under review keeps its ayah in view in the lists.
                    currentAyah =
                        focusedWord?.let { AyahRef(it.surah, it.ayah) }
                            ?: slots.reciteCurrentAyah(),
                )
            } else {
                ReciteMarks.None
            }
        val trackedWord = focusedWord ?: if (state.isReciting) slots.reciteCurrentWord() else null
        val arabicScript =
            state.settings.script.takeUnless { isGlyphScript(it) } ?: ScriptId.QpcHafs
        // Loaded off the main thread; the lists wait for the answer (a few milliseconds).
        val arabicLookup = rememberFontLookup(fontCache, arabicScript, FIRST_PAGE, isNight = false)

        // The Mushaf page is never resized or clipped: the panel is drawn over it. The lists shrink
        // to the part above the panel so their scrolling keeps the ayah in view.
        val pagerHeight = if (isMushaf) fullHeight else windowHeight
        val reciteBarHeight: Dp? =
            if (state.isReciting && bottomStackHeightPx > 0) {
                with(density) { bottomStackHeightPx.toDp() }
            } else {
                null
            }
        Box(Modifier.fillMaxWidth().height(pagerHeight).clipToBounds()) {
            QuranPager(
                state = state,
                viewModel = viewModel,
                fontCache = fontCache,
                arabicLookup = arabicLookup,
                reciteMarks = reciteMarks,
                playingWord = playingWord,
                fullHeightPx = fullHeightPx,
                reciteBarHeight = reciteBarHeight,
                trackedWord = trackedWord,
                focusedWord = focusedWord,
                onFocusWord = slots.onReciteFocusWord,
                onTrackedWordBounds = onTrackedWordBounds,
                shiftPx = currentShiftPx,
                selectionActions = selectionActions,
                onListAnchor = onListAnchor,
                onWordAnchor = { wordAnchor = it },
                // The page reports the word in its own coordinates, which start one margin in.
            )
        }
        val topClearancePx = topBarClearancePx(state.areBarsVisible)

        // Bars float over the page and never resize it.
        AnimatedVisibility(
            visible = state.areBarsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(BAR_FADE_MS)),
            exit = fadeOut(tween(BAR_FADE_MS)),
        ) {
            QuranTopBar(
                state = state,
                surahName = state.headerSurah?.localName().orEmpty(),
                isNight = slots.isNight,
                actions = topBarActions(viewModel, slots),
            )
        }
        if (!state.isPanelOpen) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                // An enabled translation is missing: say so and offer it, instead of showing none.
                if (!isMushaf) {
                    MissingTranslationPrompt(dependencies, state.settings.translations)
                }
                if (state.showBackToPlaying) {
                    Box(
                        Modifier.fillMaxWidth()
                            .padding(end = AwnSpacing.screenMargin, bottom = AwnSpacing.small)
                            .then(
                                if (state.areBarsVisible) Modifier
                                else Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                            ),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        BackToPlayingButton(onClick = viewModel::backToPlaying)
                    }
                }
                if (state.isReciting) {
                    slots.reciteBar(viewModel::exitReciteMode)
                } else {
                    AnimatedVisibility(
                        visible = state.areBarsVisible,
                        enter = fadeIn(tween(BAR_FADE_MS)),
                        exit = fadeOut(tween(BAR_FADE_MS)),
                    ) {
                        slots.audioBar(state.currentPage)
                    }
                }
            }
        }

        val range = state.selectedRange
        if (range != null && !state.isPanelOpen && !isSelecting) {
            val bounds =
                if (isMushaf) {
                    // The page may have slid up to clear the audio bar; the bar follows it.
                    pageBounds?.translate(Offset(0f, -currentShiftPx().toFloat()))
                } else {
                    listAnchor?.translate(-rootOrigin.x, -rootOrigin.y)?.let { listBlockBounds(it) }
                }
            AyahActionBar(
                bounds = bounds,
                topClearancePx = topClearancePx,
                isBookmarked = range.first in state.savedAyahs,
                playMode = state.playButtonMode,
                actions =
                    AyahBarActions(
                        onPlay = { viewModel.onPlayPressed { playRange(slots, range) } },
                        onRecite = {
                            slots.onReciteFrom(range.first)
                            viewModel.enterReciteMode()
                        },
                        onBookmark = { viewModel.toggleSavedAyah(range.first) },
                        onTranslation = viewModel::openPanel,
                        onCopy = {
                            scope.launch {
                                copyToClipboard(
                                    context,
                                    range.first,
                                    viewModel.shareTextOf(range),
                                )
                            }
                        },
                        onShare = {
                            scope.launch { shareText(context, viewModel.shareTextOf(range)) }
                        },
                    ),
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.isPanelOpen && range != null) {
            AyahPanel(
                ayah = range.first,
                chapter = state.chapters.firstOrNull { it.number == range.first.surah },
                block = state.panelBlock,
                settings = state.settings,
                onPrevious = { viewModel.selectAdjacentAyah(-1) },
                onNext = { viewModel.selectAdjacentAyah(1) },
                onClose = viewModel::closePanel,
                playTab = { PlayTab(slots, range, state.currentPage) },
                modifier =
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight),
            )
        }

        state.wordPopup?.let { popup ->
            WordPopupCard(
                popup = popup,
                anchor = wordAnchor?.translate(-rootOrigin.x, -rootOrigin.y),
                topClearancePx = topClearancePx,
                onDismiss = viewModel::dismissWordPopup,
                isPlaying = state.isPopupWordPlaying,
                onPlay = viewModel::playPopupWord,
                onStop = viewModel::stopPopupWord,
                onDownloadAudio = viewModel::downloadWordAudio,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.printOffer.isOpen) {
            PrintOfferCard(
                offer = state.printOffer,
                statuses = state.printStatuses,
                actions =
                    PrintOfferActions(
                        onChoose = viewModel::choosePrint,
                        onDownload = viewModel::downloadPrint,
                        onNotNow = viewModel::dismissPrintOffer,
                        onKeepReading = viewModel::keepReadingDuringPrintDownload,
                        onCancel = viewModel::cancelPrintDownload,
                    ),
            )
        }
        PrintFailureNotice(state.printOffer.closedFailureCount)

        if (state.popup == Popup.GoToPage) {
            GoToPageDialog(
                currentPage = state.currentPage,
                quranText = dependencies.quranText,
                script = state.settings.script,
                chapters = state.chapters,
                onGo = { page ->
                    viewModel.closePopup()
                    viewModel.jumpToPage(page)
                },
                onDismiss = viewModel::closePopup,
            )
        }
    }
}

/** What the pager tells the screen about a selection being made on a Mushaf page. */
private class MushafSelectionActions(
    val onRangeStart: () -> Unit,
    val onRangeEnd: () -> Unit,
    val onBounds: (SelectionBounds?) -> Unit,
)

private val IgnoreBounds: (SelectionBounds?) -> Unit = {}

private val IgnoreRect: (Rect?) -> Unit = {}

private val NoShift: () -> Int = { 0 }

/** The block of an ayah in a list view: its top slice stands for the first line. */
@Composable
private fun listBlockBounds(block: Rect): SelectionBounds {
    val firstLineHeight = with(LocalDensity.current) { AwnSpacing.huge.toPx() }
    return SelectionBounds(
        firstLine = block.copy(bottom = minOf(block.bottom, block.top + firstLineHeight)),
        all = block,
    )
}

/** "Play from here": the dragged range when the host can play one, else from the first ayah. */
private fun playRange(slots: QuranScreenSlots, range: AyahRange) {
    val playRange = slots.onPlayRange
    if (!range.isSingle && playRange != null) {
        playRange(range.first, range.last)
    } else {
        slots.onPlayFrom(range.first)
    }
}

/** The Play tab of the panel: the range version when the host supplies one and a range is set. */
@Composable
private fun PlayTab(slots: QuranScreenSlots, range: AyahRange, page: Int) {
    val rangePanel = slots.playPanelRange
    if (!range.isSingle && rangePanel != null) {
        rangePanel(range.first, range.last, page)
    } else {
        slots.playPanel(range.first, page)
    }
}

/** The recitation turned the page: slide like a swipe instead of jumping. */
@Composable
private fun AnimateRecitationTurns(pagerState: PagerState, state: QuranUiState) {
    LaunchedEffect(state.animatedTurnToken) {
        val targetIndex = state.currentPage - 1
        if (state.animatedTurnToken > 0 && pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }
}

// The pager with the page of each of the three views, and what each does only on the current page.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun QuranPager(
    state: QuranUiState,
    viewModel: QuranViewModel,
    fontCache: PageFontCache,
    arabicLookup: FontLookup?,
    reciteMarks: ReciteMarks,
    playingWord: State<Int?>,
    fullHeightPx: Int,
    reciteBarHeight: Dp?,
    trackedWord: WordKey?,
    focusedWord: WordKey?,
    onFocusWord: (WordKey) -> Unit,
    onTrackedWordBounds: (Rect?) -> Unit,
    shiftPx: () -> Int,
    selectionActions: MushafSelectionActions,
    onListAnchor: (Rect?) -> Unit,
    onWordAnchor: (Rect?) -> Unit,
) {
    val pagerState =
        rememberPagerState(initialPage = state.currentPage - 1, pageCount = { state.pageCount })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { index -> viewModel.onPageSettled(index + 1) }
    }
    // Jumps move the pager at once in every view; a swipe has already moved it.
    LaunchedEffect(state.jumpToken) {
        if (pagerState.currentPage != state.currentPage - 1) {
            pagerState.scrollToPage(state.currentPage - 1)
        }
    }
    LaunchedEffect(state.currentPage) { fontCache.retainAround(state.currentPage) }
    AnimateRecitationTurns(pagerState, state)
    // Dragging the pager by hand stops it turning pages for the audio.
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) viewModel.onPagerDragged()
        }
    }

    // The list scrolls clear of the top bar above and of the audio bar and the navigation bar
    // below, so a search result or the playing ayah never sits under either.
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barPadding = listContentPadding(reciteBarHeight, navigationBarHeight)
    val view = state.settings.pageView
    val tapToggle =
        remember(viewModel) {
            Modifier.pointerInput(Unit) { detectTapGestures(onTap = { viewModel.onPageTapped() }) }
        }
    // One instance of each callback for all pages, so a page whose marks did not change is skipped
    // when the current page changes.
    val onPageTapped: () -> Unit = remember(viewModel) { viewModel::onPageTapped }
    val onAyahPressed: (AyahRef) -> Unit =
        remember(viewModel, selectionActions) {
            { ayah ->
                selectionActions.onRangeStart()
                viewModel.selectAyah(ayah)
            }
        }
    val onRangeDrag: (AyahRange) -> Unit = remember(viewModel) { viewModel::selectRange }

    // Only a word that was recited badly reacts to a tap, and it opens the review card on that
    // word. The lambda is remembered so the pages do not see a new callback each time the
    // problems change.
    val currentProblems by rememberUpdatedState(reciteMarks.problems)
    val currentFocusWord by rememberUpdatedState(onFocusWord)
    val onProblemWordTap: (WordKey, Rect) -> Unit =
        remember(viewModel) {
            { key, _ ->
                if (key in currentProblems) {
                    viewModel.ignoreNextPageTap()
                    currentFocusWord(key)
                }
            }
        }

    // Page 1 is on the right, so swiping right turns to the next page.
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        beyondViewportPageCount = 1,
        key = { index -> index },
    ) { index ->
        val pageNumber = index + 1
        val isCurrent = pageNumber == state.currentPage
        LaunchedEffect(pageNumber, view) { viewModel.loadPage(pageNumber) }
        if (view == PageView.Mushaf) {
            when (val load = state.layouts[pageNumber]) {
                is PageLoad.Loaded -> {
                    // The page draws nothing until both families are known, so a swipe never
                    // flashes the Unicode fallback before the glyph font arrives.
                    val glyphLookup =
                        rememberFontLookup(
                            fontCache,
                            load.layout.script,
                            pageNumber,
                            isNight = !Awn.field.isLight,
                        )
                    val plainLookup =
                        rememberFontLookup(fontCache, ScriptId.QpcHafs, pageNumber, isNight = false)
                    if (glyphLookup == null || plainLookup == null) {
                        Box(Modifier.fillMaxSize().then(tapToggle))
                        return@HorizontalPager
                    }
                    val pageShift: () -> Int = if (isCurrent) shiftPx else NoShift
                    val shiftModifier =
                        remember(fullHeightPx, pageShift) {
                            Modifier.fixedHeightShifted(fullHeightPx, pageShift)
                        }
                    Box(Modifier.fillMaxSize().then(tapToggle).then(shiftModifier)) {
                        MushafPageItem(
                            layout = load.layout,
                            marks =
                                ScreenMarks(
                                    selection = state.selectedRange,
                                    playingAyah = state.playingAyah,
                                    playingWordPosition = playingWord,
                                    bookmarkedAyahs =
                                        if (state.settings.highlightBookmarks) state.savedAyahs
                                        else emptySet(),
                                ),
                            host =
                                MushafHost(
                                    chapters = state.chapters,
                                    fontFamily = glyphLookup.family,
                                    plainFamily = plainLookup.family,
                                    wordStates = reciteMarks.states,
                                    focusedWord = focusedWord,
                                    onWordTap = onProblemWordTap,
                                    showPageInfo = state.settings.showPageInfo,
                                    onTap = onPageTapped,
                                    pageScale = state.settings.mushafScale,
                                    onAyahPressed = onAyahPressed,
                                    onRangeDrag = onRangeDrag,
                                    onRangeEnd = selectionActions.onRangeEnd,
                                    onSelectionBounds =
                                        if (isCurrent) selectionActions.onBounds else IgnoreBounds,
                                    trackedWord = if (isCurrent) trackedWord else null,
                                    onTrackedWordBounds =
                                        if (isCurrent) onTrackedWordBounds else IgnoreRect,
                                ),
                            modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
                        )
                    }
                }
                is PageLoad.Failed ->
                    Box(Modifier.fillMaxSize().then(tapToggle), Alignment.Center) {
                        Text(
                            text = stringResource(R.string.page_unavailable),
                            style = AwnTypography.body,
                        )
                    }
                is PageLoad.Loading,
                null -> Box(Modifier.fillMaxSize())
            }
        } else if (arabicLookup == null) {
            Box(Modifier.fillMaxSize())
        } else {
            PageListView(
                content = state.contents[pageNumber],
                isCurrent = isCurrent,
                environment =
                    ListPageEnvironment(
                        settings = state.settings,
                        arabicFamily = arabicLookup.family,
                        fontCache = fontCache,
                        wordStates = reciteMarks.states,
                        isReciting = state.isReciting,
                        recitedAyah = reciteMarks.currentAyah,
                        focusedWord = focusedWord,
                        selection = state.selectedRange,
                        playingAyah = state.playingAyah,
                        playingWordPosition = playingWord,
                        isFollowing = state.isFollowing,
                        followRequest = state.jumpToken,
                        isPanelOpen = state.isPanelOpen,
                        savedAyahs = state.savedAyahs,
                        contentPadding = barPadding,
                        onTap = onPageTapped,
                        onAyahLongPress = { ayah, bounds ->
                            onListAnchor(bounds)
                            viewModel.selectAyah(ayah)
                        },
                        onWordTap =
                            listWordTap(viewModel, reciteMarks.problems, onFocusWord, onWordAnchor),
                        onUserScrolled = viewModel::onPagerDragged,
                    ),
            )
        }
    }
}

/**
 * A tap on a word of a list page: a word recited badly asks for the review card on it, any other
 * word opens its meaning card.
 */
private fun listWordTap(
    viewModel: QuranViewModel,
    problems: Map<WordKey, WordProblem>,
    onFocusWord: (WordKey) -> Unit,
    onWordAnchor: (Rect?) -> Unit,
): (AyahRef, AyahWord, Rect?) -> Unit = { ayah, word, bounds ->
    if (word.key in problems) {
        onFocusWord(word.key)
    } else {
        onWordAnchor(bounds)
        viewModel.onWordTapped(ayah, word)
    }
}

/** What recitation marks on the pages: empty while not reciting. */
private class ReciteMarks(
    val states: Map<WordKey, WordState>,
    val problems: Map<WordKey, WordProblem>,
    /** The ayah of the word the reciter is on. */
    val currentAyah: AyahRef?,
) {
    companion object {
        val None = ReciteMarks(emptyMap(), emptyMap(), currentAyah = null)
    }
}
