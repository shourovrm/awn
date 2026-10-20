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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import app.awn.quran.feature.read.page.FontDownloadPrompt
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.PageLoad
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
private const val BAR_FADE_MS = 120
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
                if (event.type == KeyEventType.KeyDown) {
                    val step =
                        when (volumeKeyAction(isVolumeDown = event.key == Key.VolumeDown)) {
                            VolumeKeyAction.NextPage -> 1
                            VolumeKeyAction.PreviousPage -> -1
                        }
                    viewModel.jumpToPage(state.currentPage + step)
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
    val fontCache =
        remember(dependencies.packs, state.packRevision) { PageFontCache(dependencies.packs) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var listAnchor by remember { mutableStateOf<Rect?>(null) }

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInWindow() },
    ) {
        val fullHeight = maxHeight
        val fullHeightPx = with(density) { fullHeight.roundToPx() }
        val panelHeight = fullHeight * PANEL_SHARE
        // The panel appears at once: Back must feel instant.
        val windowHeight = if (state.isPanelOpen) fullHeight - panelHeight else fullHeight
        val windowHeightPx = with(density) { windowHeight.roundToPx() }
        val widthPx = with(density) { maxWidth.roundToPx() }

        val currentLayout = (state.layouts[state.currentPage] as? PageLoad.Loaded)?.layout
        val mushafSpan =
            remember(currentLayout, state.selection, fullHeightPx) {
                val selected = state.selection
                if (currentLayout != null && selected != null) {
                    ayahSpan(currentLayout, selected, fullHeightPx.toFloat())
                } else {
                    null
                }
            }
        val marginPx = with(density) { AwnSpacing.medium.toPx() }
        val shiftPx =
            if (state.isPanelOpen && mushafSpan != null) {
                scrollToKeepVisible(
                        mushafSpan.top,
                        mushafSpan.bottom,
                        windowHeightPx.toFloat(),
                        marginPx,
                    )
                    .toInt()
            } else {
                0
            }

        val reciteStates = if (state.isReciting) slots.reciteWordStates() else emptyMap()
        val arabicScript =
            state.settings.script.takeUnless { isGlyphScript(it) } ?: ScriptId.QpcHafs
        val arabicFamily = fontCache.familyFor(arabicScript, FIRST_PAGE)

        Box(Modifier.fillMaxWidth().height(windowHeight).clipToBounds()) {
            QuranPager(
                state = state,
                viewModel = viewModel,
                fontCache = fontCache,
                arabicFamily = arabicFamily,
                reciteStates = reciteStates,
                fullHeightPx = fullHeightPx,
                shiftPx = shiftPx,
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
                        failed = status?.failed == true,
                        onDownload = viewModel::downloadFont,
                        onKeepFallback = viewModel::keepFallbackFont,
                    )
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

        val selection = state.selection
        if (selection != null && !state.isPanelOpen) {
            val anchor =
                if (state.settings.pageView == PageView.Mushaf) {
                    mushafSpan?.let { Rect(0f, it.top, widthPx.toFloat(), it.bottom) }
                } else {
                    listAnchor?.translate(-rootOrigin.x, -rootOrigin.y)
                }
            AyahActionBar(
                anchor = anchor,
                isBookmarked = selection in state.savedAyahs,
                actions =
                    AyahBarActions(
                        onPlay = { slots.onPlayFrom(selection) },
                        onRecite = {
                            slots.onReciteFrom(selection)
                            viewModel.enterReciteMode()
                        },
                        onBookmark = { viewModel.toggleSavedAyah(selection) },
                        onTranslation = viewModel::openPanel,
                        onCopy = {
                            scope.launch {
                                copyToClipboard(
                                    context,
                                    selection,
                                    viewModel.shareTextOf(selection),
                                )
                            }
                        },
                        onShare = {
                            scope.launch {
                                shareText(context, viewModel.shareTextOf(selection))
                            }
                        },
                    ),
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.isPanelOpen && selection != null) {
            AyahPanel(
                ayah = selection,
                chapter = state.chapters.firstOrNull { it.number == selection.surah },
                block = state.panelBlock,
                settings = state.settings,
                onPrevious = { viewModel.selectAdjacentAyah(-1) },
                onNext = { viewModel.selectAdjacentAyah(1) },
                onClose = viewModel::closePanel,
                playTab = { slots.playPanel(selection, state.currentPage) },
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
    fullHeightPx: Int,
    shiftPx: Int,
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
                        MushafPageHost(
                            layout = load.layout,
                            chapters = state.chapters,
                            fontFamily = fontCache.familyFor(load.layout.script, pageNumber),
                            plainFamily = fontCache.familyFor(ScriptId.QpcHafs, pageNumber),
                            highlights =
                                MushafHighlights(
                                    selectedAyah = state.selection,
                                    playingAyah = state.playingAyah,
                                    playingWord =
                                        state.playingAyah?.let { ayah ->
                                            state.playingWordPosition?.let { position ->
                                                WordKey(ayah.surah, ayah.ayah, position)
                                            }
                                        },
                                    bookmarkedAyahs = state.savedAyahs,
                                ),
                            wordStates = reciteStates,
                            showPageInfo = state.settings.showPageInfo,
                            onTap = viewModel::toggleBars,
                            onAyahPressed = viewModel::selectAyah,
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
                        selection = state.selection,
                        playingAyah = state.playingAyah,
                        playingWordPosition = state.playingWordPosition,
                        savedAyahs = state.savedAyahs,
                        contentPadding = barPadding,
                        onTap = viewModel::toggleBars,
                        onAyahLongPress = { ayah, bounds ->
                            onListAnchor(bounds)
                            viewModel.selectAyah(ayah)
                        },
                        onWordTap = viewModel::onWordTapped,
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
            .clickable(onClick = onDismiss)
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
