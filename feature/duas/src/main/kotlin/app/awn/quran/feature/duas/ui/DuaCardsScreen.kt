@file:Suppress("MatchingDeclarationName") // DuaCardsActions is the contract; the screen follows it.

package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.data.CardLayers
import app.awn.quran.feature.duas.state.DuasState
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.launch

/** What the buttons of the card screen do. */
internal class DuaCardsActions(
    val onBack: () -> Unit,
    val onSearch: () -> Unit,
    val onToggleSaved: (Dua) -> Unit,
    val onOpenMushaf: (Dua) -> Unit,
    val onCleanRecitation: (Dua) -> Unit,
    val onRequestWordTiles: (Dua) -> Unit,
)

/**
 * One dua per card, swiped sideways. The bar names the group and the position; the panel under the
 * cards holds the controls of the chosen mode.
 */
@Composable
internal fun DuaCardsScreen(
    title: String,
    duas: List<Dua>,
    startIndex: Int,
    state: DuasState,
    dependencies: DuasDependencies,
    actions: DuaCardsActions,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { duas.size }
    val dua = duas[pagerState.currentPage]
    val screenState = rememberCardScreenState()
    val playback by dependencies.audioPlayer.state.collectAsState(initial = PlaybackState.Idle)
    StopAudioWhenLeavingCard(pagerState, duas, dependencies, playback)
    val layers = state.progress.layers
    val lineView = rememberLineViewResources(dependencies)
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pagerState.currentPage) { screenState.wordCard = null }

    Box(modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInWindow() }) {
        // The open word card is modal: the screen under it is hidden from screen readers, and a
        // second Play button there would be mistaken for the card's own.
        val underCardSemantics =
            if (screenState.wordCard != null) Modifier.clearAndSetSemantics {} else Modifier
        Column(Modifier.fillMaxSize().then(underCardSemantics)) {
            CardsTopBar(
                title = title,
                subtitle =
                    stringResource(R.string.duas_position, pagerState.currentPage + 1, duas.size),
                dua = dua,
                state = state,
                dependencies = dependencies,
                actions = actions,
            )
            CardPager(
                pagerState = pagerState,
                duas = duas,
                state = state,
                screenState = screenState,
                dependencies = dependencies,
                lineView = lineView,
                playback = playback,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
            LayerPronunciationOffer(layers, dependencies)
            ModePanel(
                dua = dua,
                state = state,
                screenState = screenState,
                dependencies = dependencies,
                playback = playback,
                actions = actions,
                modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
            )
            DuaModeBar(selected = screenState.mode, onSelect = { screenState.mode = it })
        }
        screenState.wordCard?.let { target ->
            DuaWordCard(
                target = target,
                dependencies = dependencies,
                rootOrigin = rootOrigin,
                topClearancePx = topClearancePx(),
                onDismiss = { screenState.wordCard = null },
            )
        }
    }
}

/** The room the top bar and the status bar take; a word card never covers it. */
@Composable
private fun topClearancePx(): Float {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    return statusBarPx + with(density) { AwnSpacing.topBarHeight.toPx() }
}

@Composable
private fun SaveButton(isSaved: Boolean, onClick: () -> Unit) {
    AwnIconButton(
        icon = if (isSaved) R.drawable.ic_duas_bookmark_filled else R.drawable.ic_duas_bookmark,
        contentDescription =
            stringResource(if (isSaved) R.string.duas_unsave else R.string.duas_save),
        onClick = onClick,
        tint = Awn.field.onBar,
    )
}

@Composable
private fun CardPager(
    pagerState: PagerState,
    duas: List<Dua>,
    state: DuasState,
    screenState: CardScreenState,
    dependencies: DuasDependencies,
    lineView: LineViewResources?,
    playback: PlaybackState,
    actions: DuaCardsActions,
    modifier: Modifier,
) {
    val wordStates by dependencies.recitation.wordStates.collectAsState()
    val wordProblems by dependencies.recitation.wordProblems.collectAsState()
    HorizontalPager(state = pagerState, modifier = modifier) { index ->
        val dua = duas[index]
        val isCurrent = index == pagerState.currentPage
        val isReciting = isCurrent && screenState.mode == DuaCardMode.Recite
        // The meanings and the sound of the words come with the tiles.
        // Every word opens its card, which shows the meaning, so the meanings load with the card.
        LaunchedEffect(dua.id) { actions.onRequestWordTiles(dua) }
        val highlights =
            if (isCurrent) {
                CardHighlights(
                    playingWord = playback.playingWordOf(dua),
                    wordStates = if (isReciting) wordStates else emptyMap(),
                    flaggedWords = if (isReciting) wordProblems.keys else emptySet(),
                    focusedWord = screenState.reviewedWord,
                )
            } else {
                CardHighlights()
            }
        DuaCardPage(
            card = state.cards[dua.id],
            wordTiles = state.wordTiles[dua.id].orEmpty(),
            state =
                CardPageState(
                    mode = screenState.mode,
                    showDuaOnly = screenState.showDuaOnly,
                    isTranslationHidden = isReciting && screenState.isHidingWords,
                    highlights = highlights,
                    layers = state.progress.layers,
                ),
            lineView = lineView,
            actions =
                CardPageActions(
                    onToggleDuaOnly = { screenState.showDuaOnly = !screenState.showDuaOnly },
                    onOpenMushaf = { actions.onOpenMushaf(dua) },
                    onWordTap = { ayah, word, bounds ->
                        screenState.wordCard = WordCardTarget(ayah, word, bounds)
                    },
                    onFlaggedWordClick = { key -> screenState.reviewedWord = key },
                ),
        )
    }
}

@Composable
private fun ModePanel(
    dua: Dua,
    state: DuasState,
    screenState: CardScreenState,
    dependencies: DuasDependencies,
    playback: PlaybackState,
    actions: DuaCardsActions,
    modifier: Modifier,
) {
    val card = state.cards[dua.id]
    when (screenState.mode) {
        DuaCardMode.Listen -> ListenSection(dua, card, dependencies, playback, modifier)
        DuaCardMode.Recite ->
            ReciteSection(
                dua = dua,
                card = card,
                dependencies = dependencies,
                cleanCount = state.progress.cleanCount(dua.id),
                isHidingWords = screenState.isHidingWords,
                reviewedWord = screenState.reviewedWord,
                actions =
                    ReciteSectionActions(
                        onToggleHideWords = {
                            screenState.isHidingWords = !screenState.isHidingWords
                            dependencies.recitation.setHideWords(screenState.isHidingWords)
                        },
                        onReviewedWordChange = { screenState.reviewedWord = it },
                        onCleanRecitation = { actions.onCleanRecitation(dua) },
                        onCancel = { screenState.mode = DuaCardMode.Listen },
                    ),
                modifier = modifier,
            )
    }
}

/**
 * Audio of one dua must not go on under another card or after the screen is left. Audio that the
 * user started elsewhere is left alone.
 */
@Composable
private fun StopAudioWhenLeavingCard(
    pagerState: PagerState,
    duas: List<Dua>,
    dependencies: DuasDependencies,
    playback: PlaybackState,
) {
    val latestPlayback by rememberUpdatedState(playback)
    var shownPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != shownPage) {
            dependencies.audioPlayer.stop()
            shownPage = pagerState.currentPage
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (duas.any { latestPlayback.ayahOf(it) != null }) dependencies.audioPlayer.stop()
        }
    }
}

/** The bar over the cards: back, the group and position, then layers, search and save. */
@Composable
private fun CardsTopBar(
    title: String,
    subtitle: String,
    dua: Dua,
    state: DuasState,
    dependencies: DuasDependencies,
    actions: DuaCardsActions,
) {
    val scope = rememberCoroutineScope()
    val saveLayers: (CardLayers) -> Unit = { chosen ->
        scope.launch { dependencies.progress.setLayers(chosen) }
    }
    DuasTopBar(title = title, subtitle = subtitle, onBack = actions.onBack) {
        CardLayersButton(layers = state.progress.layers, onLayersChange = saveLayers)
        AwnIconButton(
            icon = UiR.drawable.ic_ui_search,
            contentDescription = stringResource(R.string.duas_search_button),
            onClick = actions.onSearch,
            tint = Awn.field.onBar,
        )
        SaveButton(
            isSaved = state.progress.isSaved(dua.id),
            onClick = { actions.onToggleSaved(dua) },
        )
    }
}

/** Word by word and transliteration need the pronunciation pack; offer it while one is on. */
@Composable
private fun LayerPronunciationOffer(layers: CardLayers, dependencies: DuasDependencies) {
    if (!layers.showWordByWord && !layers.showTransliteration) return
    PronunciationOffer(
        dependencies,
        Modifier.padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    )
}
