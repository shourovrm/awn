package app.awn.quran.feature.read.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.ReadHeader
import app.awn.quran.feature.read.ReadToolbar
import app.awn.quran.feature.read.dock.ReadPlayerDock
import app.awn.quran.feature.read.localName
import app.awn.quran.tracker.WordState

private const val PERCENT = 100

/**
 * The Mushaf view: a pager of printed pages with the header, toolbar, translation sheet and the
 * collapsed player dock.
 *
 * [startPage] is the page to open, or null for the one read last. The script comes from the read
 * settings. [onTogglePageView] switches to the ayah-by-ayah view, [onBrowse] opens the lists and
 * [onExpandPlayer] opens the full player.
 */
@Composable
fun MushafPageScreen(
    dependencies: ReadDependencies,
    startPage: Int?,
    onTogglePageView: () -> Unit,
    onBrowse: () -> Unit,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    reciterName: (String) -> String = { it },
) {
    val viewModel: MushafViewModel =
        viewModel(factory = viewModelFactory { initializer { MushafViewModel(dependencies) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(startPage) { viewModel.start(startPage) }

    val fontCache =
        remember(dependencies.packs, state.packRevision) { PageFontCache(dependencies.packs) }
    LaunchedEffect(fontCache, state.currentPage) { fontCache.retainAround(state.currentPage) }

    val currentLoad = state.pages[state.currentPage]
    val isFontMissing =
        currentLoad is PageLoad.Loaded &&
            isGlyphScript(state.settings.script) &&
            fontCache.familyFor(state.settings.script, state.currentPage) == null

    Column(modifier.fillMaxSize()) {
        ReadHeader(
            surahName = state.headerSurah?.localName().orEmpty(),
            juz = state.juz,
            page = state.currentPage,
        )
        ReadToolbar(
            primaryLabel =
                stringResource(
                    if (state.isBookmarked) R.string.action_bookmarked
                    else R.string.action_bookmark,
                ),
            onPrimary = viewModel::toggleBookmark,
            viewSwitchLabel = stringResource(R.string.action_ayah_list),
            onViewSwitch = onTogglePageView,
            onBrowse = onBrowse,
        )
        if (isFontMissing) {
            FontOffer(
                progress = state.fontDownloadProgress,
                failed = state.fontDownloadFailed,
                onDownload = viewModel::downloadFont,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin)) {
            if (state.hasStarted) {
                MushafPager(
                    state = state,
                    fontCache = fontCache,
                    onPageSettled = viewModel::onPageSettled,
                    onPageNeeded = viewModel::loadPage,
                    onWordClick = viewModel::onWordTapped,
                )
            }
        }
        SheetHost(state.sheet, viewModel)
        ReadPlayerDock(
            audioPlayer = dependencies.audioPlayer,
            onExpand = onExpandPlayer,
            reciterName = reciterName,
        )
    }
}

@Composable
private fun SheetHost(sheet: AyahSheet?, viewModel: MushafViewModel) {
    AnimatedVisibility(
        visible = sheet != null,
        enter = slideInVertically { fullHeight -> fullHeight },
        exit = ExitTransition.None,
    ) {
        sheet?.let {
            TranslationSheet(
                sheet = it,
                onToggleSaved = viewModel::toggleSavedAyah,
                onClose = viewModel::dismissSheet,
            )
        }
    }
}

@Composable
private fun MushafPager(
    state: MushafUiState,
    fontCache: PageFontCache,
    onPageSettled: (Int) -> Unit,
    onPageNeeded: (Int) -> Unit,
    onWordClick: (WordKey) -> Unit,
) {
    val pagerState =
        rememberPagerState(initialPage = state.currentPage - 1, pageCount = { state.pageCount })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index -> onPageSettled(index + 1) }
    }
    // A jump from the lists or a new start page moves the pager to the page the model now holds.
    LaunchedEffect(state.currentPage) {
        if (pagerState.settledPage + 1 != state.currentPage) {
            pagerState.scrollToPage(state.currentPage - 1)
        }
    }

    // Page 1 is on the right, so swiping left turns to the next page.
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        beyondViewportPageCount = 1,
        key = { index -> index },
    ) { index ->
        val pageNumber = index + 1
        LaunchedEffect(pageNumber) { onPageNeeded(pageNumber) }
        when (val load = state.pages[pageNumber]) {
            is PageLoad.Loaded ->
                MushafPage(
                    layout = load.layout,
                    chapters = state.chapters,
                    fontFamily = fontCache.familyFor(load.layout.script, pageNumber),
                    plainFamily = fontCache.familyFor(ScriptId.QpcHafs, pageNumber),
                    zoom = state.settings.textSize.zoom,
                    selectedWord = state.selectedWord,
                    wordStates = { WordState.Neutral },
                    onWordClick = onWordClick,
                )
            is PageLoad.Failed ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = stringResource(R.string.page_unavailable),
                        style = AwnTypography.body,
                    )
                }
            is PageLoad.Loading,
            null -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FontOffer(progress: Float?, failed: Boolean, onDownload: () -> Unit) {
    val label =
        when {
            progress != null ->
                stringResource(R.string.font_downloading, (progress * PERCENT).toInt())
            failed -> stringResource(R.string.font_download_failed)
            else -> stringResource(R.string.font_download)
        }
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(enabled = progress == null, role = Role.Button, onClick = onDownload)
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    )
}

/** The green sheet that rises under the page with the translations of the tapped word's ayah. */
@Composable
private fun TranslationSheet(sheet: AyahSheet, onToggleSaved: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(AwnColors.green)
            .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.large),
    ) {
        sheet.translations.forEachIndexed { index, translation ->
            val textStyle =
                if (index == 0) {
                    AwnTypography.lead.copy(
                        fontWeight = FontWeight.Normal,
                        lineHeight = AwnTypography.body.lineHeight,
                    )
                } else {
                    AwnTypography.body
                }
            Text(text = translation.text, style = textStyle, color = AwnColors.paper)
            val caption =
                listOfNotNull(
                        translation.name,
                        translation.rangeLabel,
                    )
                    .joinToString(separator = " · ")
            Text(
                text = caption,
                style = AwnTypography.small,
                color = AwnColors.paper,
                modifier = Modifier.padding(top = AwnSpacing.small, bottom = AwnSpacing.medium),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SheetAction(
                label =
                    stringResource(
                        if (sheet.isSaved) R.string.sheet_saved else R.string.sheet_save_ayah,
                    ),
                onClick = onToggleSaved,
                modifier = Modifier.weight(1f),
            )
            SheetAction(
                label = stringResource(R.string.sheet_close),
                onClick = onClose,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = AwnColors.ochre,
        modifier =
            modifier
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
    )
}
