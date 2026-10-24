package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.Dua
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

    Column(modifier.fillMaxSize()) {
        DuasTopBar(
            title = title,
            subtitle =
                stringResource(R.string.duas_position, pagerState.currentPage + 1, duas.size),
            onBack = actions.onBack,
        ) {
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
        CardPager(
            pagerState = pagerState,
            duas = duas,
            state = state,
            screenState = screenState,
            dependencies = dependencies,
            playback = playback,
            actions = actions,
            modifier = Modifier.weight(1f),
        )
        PageDots(count = duas.size, current = pagerState.currentPage)
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
    playback: PlaybackState,
    actions: DuaCardsActions,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val wordStates by dependencies.recitation.wordStates.collectAsState()
    val wordProblems by dependencies.recitation.wordProblems.collectAsState()
    HorizontalPager(state = pagerState, modifier = modifier) { index ->
        val dua = duas[index]
        val isCurrent = index == pagerState.currentPage
        val isReciting = isCurrent && screenState.mode == DuaCardMode.Recite
        LaunchedEffect(dua.id, screenState.mode) {
            if (screenState.mode == DuaCardMode.WordByWord) actions.onRequestWordTiles(dua)
        }
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
                ),
            myWords = dependencies.myWords,
            actions =
                CardPageActions(
                    onToggleDuaOnly = { screenState.showDuaOnly = !screenState.showDuaOnly },
                    onOpenMushaf = { actions.onOpenMushaf(dua) },
                    onPlayWord = { key -> scope.launch { dependencies.playWord(key) } },
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
        DuaCardMode.WordByWord -> Unit
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
