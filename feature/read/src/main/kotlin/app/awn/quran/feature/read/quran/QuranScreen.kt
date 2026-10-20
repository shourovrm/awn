@file:Suppress(
    "MatchingDeclarationName",
) // QuranScreenSlots is the contract; the screen follows it.

package app.awn.quran.feature.read.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.FontDownloadPrompt
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.PageLoad
import app.awn.quran.feature.read.page.SelectionBounds
import app.awn.quran.feature.read.page.isGlyphScript
import app.awn.quran.feature.read.page.pageTypeText
import app.awn.quran.feature.read.settings.PageView
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
private const val LONG_PRESS_MS = 500L

/** Bars fade in and out in this long: the first change on screen comes well inside 150 ms. */
internal const val BAR_FADE_MS = 120
private const val BANGLA_LANGUAGE = "bn"
private const val ENGLISH_LANGUAGE = "en"
private const val FIRST_PAGE = 1

/**
 * The platform view configuration with the 500 ms long press the ayah selection is specified for.
 */
private class FixedLongPress(private val base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long = LONG_PRESS_MS
}

/**
 * The Quran screen: a pager of the 604 pages in one of three views, with a top bar, the audio or
 * recite bar, the ayah selection bar and the ayah panel over it. [target] opens a page, or an ayah
 * that is selected; null opens the page read last. Search results and the index pass a target.
 *
 * Back closes popups, the ayah panel, the selection, recite mode and the bars, in that order, and
 * is left to the host only when none is open.
 */
@Composable
fun QuranScreen(
    dependencies: ReadDependencies,
    slots: QuranScreenSlots,
    modifier: Modifier = Modifier,
    target: QuranTarget? = null,
) {
    val language = LocalConfiguration.current.locales[0].language
    val wordLanguage = if (language == BANGLA_LANGUAGE) BANGLA_LANGUAGE else ENGLISH_LANGUAGE
    val viewModel: QuranViewModel =
        viewModel(
            factory =
                viewModelFactory { initializer { QuranViewModel(dependencies, wordLanguage) } },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(target) { viewModel.open(target) }
    FollowRecitation(slots, viewModel, isReciting = state.isReciting)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.setScreenActive(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.setScreenActive(false) }
    BackHandler(enabled = state.hasBackWork) { viewModel.onBack() }

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

    CompositionLocalProvider(
        LocalViewConfiguration provides FixedLongPress(LocalViewConfiguration.current),
    ) {
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
        remember(dependencies.packs, state.packRevision) { PageFontCache(dependencies.packs) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var listAnchor by remember { mutableStateOf<Rect?>(null) }
    // Where the selected ayahs are on the Mushaf page, in the page's own (unshifted) coordinates.
    var mushafBounds by remember { mutableStateOf<SelectionBounds?>(null) }
    // True from the long press until the finger lifts; the bar waits for the release.
    var isSelecting by remember { mutableStateOf(false) }

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
        val shiftPx =
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

        val reciteStates = if (state.isReciting) slots.reciteWordStates() else emptyMap()
        val arabicScript =
            state.settings.script.takeUnless { isGlyphScript(it) } ?: ScriptId.QpcHafs
        val arabicFamily = fontCache.familyFor(arabicScript, FIRST_PAGE)

        // The Mushaf page is never resized or clipped: the panel is drawn over it. The lists shrink
        // to the part above the panel so their scrolling keeps the ayah in view.
        val pagerHeight = if (isMushaf) fullHeight else windowHeight
        Box(Modifier.fillMaxWidth().height(pagerHeight).clipToBounds()) {
            QuranPager(
                state = state,
                viewModel = viewModel,
                fontCache = fontCache,
                arabicFamily = arabicFamily,
                reciteStates = reciteStates,
                playingWord = playingWord,
                fullHeightPx = fullHeightPx,
                shiftPx = shiftPx,
                selectionActions =
                    MushafSelectionActions(
                        onRangeStart = { isSelecting = true },
                        onRangeEnd = { isSelecting = false },
                        onBounds = { mushafBounds = it },
                    ),
                onListAnchor = { listAnchor = it },
            )
        }

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
                // The chosen print's font is missing: offer it above the bar, page stays readable.
                if (state.showFontPrompt && state.settings.pageView == PageView.Mushaf) {
                    val status = state.fontStatus
                    FontDownloadPrompt(
                        pageTypeName = stringResource(pageTypeText(state.settings.script).name),
                        sizeBytes = status?.downloadBytes,
                        progress = status?.progress,
                        failure = status?.failure,
                        onDownload = viewModel::downloadFont,
                        onKeepFallback = viewModel::keepFallbackFont,
                    )
                }
                // An enabled translation is missing: say so and offer it, instead of showing none.
                if (state.settings.pageView == PageView.Translation) {
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
                    pageBounds
                } else {
                    listAnchor?.translate(-rootOrigin.x, -rootOrigin.y)?.let { listBlockBounds(it) }
                }
            AyahActionBar(
                bounds = bounds,
                isBookmarked = range.first in state.savedAyahs,
                actions =
                    AyahBarActions(
                        onPlay = { playRange(slots, range) },
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
                onDismiss = viewModel::dismissWordPopup,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (state.popup == Popup.GoToPage) {
            GoToPageDialog(
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

private fun topBarActions(viewModel: QuranViewModel, slots: QuranScreenSlots) =
    TopBarActions(
        onOpenIndex = slots.onOpenIndex,
        onCycleView = viewModel::cycleView,
        onToggleNight = slots.onToggleNight,
        onOpenTextSize = { viewModel.openPopup(Popup.TextSize) },
        onOpenOverflow = { viewModel.openPopup(Popup.Overflow) },
        onDismissPopup = viewModel::closePopup,
        onTextSizeChange = viewModel::changeTextSize,
        onSearch = {
            viewModel.closePopup()
            slots.onOpenSearch()
        },
        onGoToPage = { viewModel.openPopup(Popup.GoToPage) },
        onBookmarkPage = {
            viewModel.closePopup()
            viewModel.toggleBookmarkOfPage()
        },
        onLearn = {
            viewModel.closePopup()
            slots.onOpenLearn()
        },
        onSettings = slots.onOpenSettings,
    )

@Suppress("LongMethod") // The pager with the page of each of the three views.
@Composable
private fun QuranPager(
    state: QuranUiState,
    viewModel: QuranViewModel,
    fontCache: PageFontCache,
    arabicFamily: FontFamily?,
    reciteStates: Map<WordKey, WordState>,
    playingWord: State<Int?>,
    fullHeightPx: Int,
    shiftPx: Int,
    selectionActions: MushafSelectionActions,
    onListAnchor: (Rect?) -> Unit,
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
    // Dragging the pager by hand stops it turning pages for the audio.
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) viewModel.onPagerDragged()
        }
    }

    val barPadding =
        PaddingValues(
            vertical = AwnSpacing.huge + AwnSpacing.small,
            horizontal = AwnSpacing.screenMargin,
        )
    val view = state.settings.pageView
    val tapToggle =
        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { viewModel.toggleBars() }) }

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
                is PageLoad.Loaded ->
                    Box(
                        Modifier.fillMaxSize().then(tapToggle).fixedHeightShifted(fullHeightPx) {
                            if (isCurrent) shiftPx else 0
                        },
                    ) {
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
                                    fontFamily =
                                        fontCache.familyFor(load.layout.script, pageNumber),
                                    plainFamily = fontCache.familyFor(ScriptId.QpcHafs, pageNumber),
                                    wordStates = reciteStates,
                                    showPageInfo = state.settings.showPageInfo,
                                    onTap = viewModel::toggleBars,
                                    onAyahPressed = { ayah ->
                                        selectionActions.onRangeStart()
                                        viewModel.selectAyah(ayah)
                                    },
                                    onRangeDrag = viewModel::selectRange,
                                    onRangeEnd = selectionActions.onRangeEnd,
                                    onSelectionBounds =
                                        if (isCurrent) selectionActions.onBounds else IgnoreBounds,
                                ),
                            modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
                        )
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
        } else {
            PageListView(
                content = state.contents[pageNumber],
                isCurrent = isCurrent,
                environment =
                    ListPageEnvironment(
                        mode =
                            if (view == PageView.Translation) ListMode.Translation
                            else ListMode.WordByWord,
                        settings = state.settings,
                        arabicFamily = arabicFamily,
                        selection = state.selectedRange,
                        playingAyah = state.playingAyah,
                        playingWordPosition = playingWord,
                        isFollowing = state.isFollowing,
                        followRequest = state.jumpToken,
                        isPanelOpen = state.isPanelOpen,
                        savedAyahs = state.savedAyahs,
                        contentPadding = barPadding,
                        onTap = viewModel::toggleBars,
                        onAyahLongPress = { ayah, bounds ->
                            onListAnchor(bounds)
                            viewModel.selectAyah(ayah)
                        },
                        onWordTap = viewModel::onWordTapped,
                        onUserScrolled = viewModel::onPagerDragged,
                    ),
            )
        }
    }
}

/** A small card with the tapped word, its meaning and its transliteration. */
@Composable
private fun WordPopupCard(popup: WordPopup, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val field = Awn.field
    Column(
        modifier
            .padding(bottom = AwnSpacing.dockHeight + AwnSpacing.medium)
            .shadow(AwnSpacing.small, AwnShapes.control)
            .background(field.buttonFill, AwnShapes.control)
            .clickable(
                onClickLabel = stringResource(R.string.panel_close),
                role = Role.Button,
                onClick = onDismiss,
            )
            .padding(AwnSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(text = popup.word.arabic, style = AwnTypography.lead, color = field.onButton)
        popup.word.meaning?.let {
            Text(text = it, style = AwnTypography.body, color = field.onButton)
        }
        popup.word.transliteration?.let {
            Text(text = it, style = AwnTypography.small, color = field.onButton)
        }
    }
}

private fun copyToClipboard(context: Context, ayah: AyahRef, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(ayah.toString(), text))
}

private fun shareText(context: Context, text: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser =
        Intent.createChooser(send, context.getString(R.string.ayah_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
