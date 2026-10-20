package app.awn.quran.feature.read.ayah

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.ReadHeader
import app.awn.quran.feature.read.ReadToolbar
import app.awn.quran.feature.read.dock.ReadPlayerDock
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.isGlyphScript
import app.awn.quran.feature.read.settings.ReadSettings

private const val BANGLA_LANGUAGE = "bn"
private const val ENGLISH_LANGUAGE = "en"
private const val CURRENT_BLOCK_ALPHA = 0.08f

private val wordMeaningStyle = AwnTypography.caption.copy(textAlign = TextAlign.Center)

/**
 * The ayah-by-ayah view of [source]. Each line of a block (word by word, transliteration, each
 * translation) follows the read settings. [onTogglePageView] returns to the Mushaf view.
 */
@Composable
fun AyahListScreen(
    dependencies: ReadDependencies,
    source: AyahListSource,
    onTogglePageView: () -> Unit,
    onBrowse: () -> Unit,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    reciterName: (String) -> String = { it },
) {
    val language = LocalConfiguration.current.locales[0].language
    val wordLanguage = if (language == BANGLA_LANGUAGE) BANGLA_LANGUAGE else ENGLISH_LANGUAGE
    val viewModel: AyahListViewModel =
        viewModel(
            key = source.toString(),
            factory =
                viewModelFactory {
                    initializer { AyahListViewModel(dependencies, source, wordLanguage) }
                },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontCache = remember(dependencies.packs) { PageFontCache(dependencies.packs) }
    val arabicScript = state.settings.script.takeUnless { isGlyphScript(it) } ?: ScriptId.QpcHafs
    val arabicFamily = fontCache.familyFor(arabicScript, FIRST_PAGE)

    Column(modifier.fillMaxSize()) {
        ReadHeader(
            surahName = state.headerSurah?.localName().orEmpty(),
            juz = state.juz,
            page = state.page,
        )
        ReadToolbar(
            primaryLabel =
                stringResource(
                    if (state.isBookmarked) R.string.action_bookmarked
                    else R.string.action_bookmark,
                ),
            onPrimary = viewModel::toggleBookmark,
            viewSwitchLabel = stringResource(R.string.action_mushaf),
            onViewSwitch = onTogglePageView,
            onBrowse = onBrowse,
        )
        if (state.source is AyahListSource.Page) {
            PageStepper(
                onPrevious = viewModel::openPreviousPage,
                onNext = viewModel::openNextPage,
            )
        }
        AyahItems(
            state = state,
            arabicFamily = arabicFamily,
            viewModel = viewModel,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        ReadPlayerDock(
            audioPlayer = dependencies.audioPlayer,
            onExpand = onExpandPlayer,
            reciterName = reciterName,
        )
    }
}

private const val FIRST_PAGE = 1

@Composable
private fun AyahItems(
    state: AyahListUiState,
    arabicFamily: FontFamily?,
    viewModel: AyahListViewModel,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = AwnSpacing.screenMargin),
    ) {
        items(items = state.ayahs, key = { it.toString() }) { ayah ->
            LaunchedEffect(ayah, state.settings.translations) { viewModel.ensureBlock(ayah) }
            val block = state.blocks[ayah]
            if (block != null) {
                AyahBlockView(
                    block = block,
                    settings = state.settings,
                    arabicFamily = arabicFamily,
                    isCurrent = ayah == state.currentAyah,
                    onClick = { viewModel.onAyahTapped(ayah) },
                )
            }
        }
    }
}

@Composable
private fun PageStepper(onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepperAction(label = stringResource(R.string.action_previous_page), onClick = onPrevious)
        StepperAction(label = stringResource(R.string.action_next_page), onClick = onNext)
    }
}

@Composable
private fun StepperAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(onClick = onClick)
                .padding(AwnSpacing.small),
    )
}

@Composable
private fun AyahBlockView(
    block: AyahBlock,
    settings: ReadSettings,
    arabicFamily: FontFamily?,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val tint =
        if (isCurrent) Awn.field.buttonFill.copy(alpha = CURRENT_BLOCK_ALPHA) else Color.Transparent
    Column(
        Modifier.fillMaxWidth()
            .padding(vertical = AwnSpacing.small)
            .clip(AwnShapes.control)
            .background(tint)
            .clickable(onClick = onClick)
            .padding(vertical = AwnSpacing.medium, horizontal = AwnSpacing.small),
    ) {
        WordsRow(
            block = block,
            showMeaning = settings.showWordByWord,
            arabicStyle =
                AwnTypography.quran.copy(
                    fontSize = AwnTypography.quran.fontSize * settings.textSize.zoom,
                    fontFamily = arabicFamily ?: AwnTypography.quran.fontFamily,
                ),
        )
        if (settings.showTransliteration && block.transliteration != null) {
            Text(
                text = block.transliteration,
                style = AwnTypography.body,
                color = Awn.field.buttonFill,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
        block.translations.forEach { translation ->
            val label = translation.rangeLabel?.let { "$it  " }.orEmpty()
            Text(
                text = label + translation.text,
                style = AwnTypography.body,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

/** The rosette first, then the words right to left, each with its meaning under it. */
@Composable
private fun WordsRow(block: AyahBlock, showMeaning: Boolean, arabicStyle: TextStyle) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterVertically)
                        .size(AwnSpacing.large)
                        .clip(CircleShape)
                        .background(AwnColors.ochre),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = block.ayah.ayah.toString(),
                    style = AwnTypography.small,
                    color = AwnColors.ink,
                    maxLines = 1,
                )
            }
            block.words.forEach { word ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = word.arabic, style = arabicStyle)
                    if (showMeaning && word.meaning != null) {
                        Text(
                            text = word.meaning,
                            style = wordMeaningStyle,
                            modifier = Modifier.widthIn(max = AwnSpacing.giant + AwnSpacing.large),
                        )
                    }
                }
            }
        }
    }
}
