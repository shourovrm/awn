package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.BASMALA_TEXT
import app.awn.quran.feature.read.settings.ReadSettings

/** Which of the two list views a page is drawn in. */
internal enum class ListMode {
    Translation,
    WordByWord,
}

/** The fills behind ayahs and words in the list views, taken from the colour mode. */
internal class ReaderFills(
    val selectedAyah: Color,
    val playingAyah: Color,
    val playingWord: Color,
    val bookmarkedAyah: Color,
)

@Composable
internal fun readerFills(): ReaderFills {
    val field = Awn.field
    return ReaderFills(
        selectedAyah = field.selectedAyahFill,
        playingAyah = field.playingAyahFill,
        playingWord = field.playingWordFill,
        bookmarkedAyah = field.bookmarkedAyahFill,
    )
}

/** What a list page needs to know about the screen it sits in. */
@Suppress("LongParameterList") // Plain data and callbacks handed from the screen to each page.
internal class ListPageEnvironment(
    val mode: ListMode,
    val settings: ReadSettings,
    val arabicFamily: FontFamily?,
    val selection: AyahRef?,
    val playingAyah: AyahRef?,
    val playingWordPosition: Int?,
    val savedAyahs: Set<AyahRef>,
    val contentPadding: PaddingValues,
    val onTap: () -> Unit,
    /** The ayah the reader pressed for 500 ms, with its bounds in window coordinates. */
    val onAyahLongPress: (AyahRef, Rect?) -> Unit,
    val onWordTap: (AyahRef, AyahWord) -> Unit,
)

/**
 * One page of the Translation or Word by word view: a list of ayah blocks with a band at each
 * surah. When [isCurrent], the list scrolls the playing ayah, or a newly selected one, to the top.
 */
@Composable
internal fun PageListView(
    content: PageContent?,
    isCurrent: Boolean,
    environment: ListPageEnvironment,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val fills = readerFills()
    if (content != null && isCurrent) {
        LaunchedEffect(content, environment.playingAyah) {
            val index = environment.playingAyah?.let(content::indexOf) ?: -1
            if (index >= 0) listState.animateScrollToItem(index)
        }
        LaunchedEffect(content, environment.selection) {
            val index = environment.selection?.let(content::indexOf) ?: -1
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }
    LazyColumn(
        state = listState,
        modifier =
            modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { environment.onTap() })
            },
        contentPadding = environment.contentPadding,
    ) {
        items(items = content?.items.orEmpty(), key = { itemKey(it) }) { item ->
            when (item) {
                is PageItem.SurahBand -> SurahBand(item)
                is PageItem.Basmalah -> BasmalahBlock(environment)
                is PageItem.Ayah -> AyahItem(item.block, environment, fills)
            }
        }
    }
}

private fun itemKey(item: PageItem): String =
    when (item) {
        is PageItem.SurahBand -> "band-${item.chapter.number}"
        is PageItem.Basmalah -> "basmalah-${item.surah}"
        is PageItem.Ayah -> "ayah-${item.block.ayah}"
    }

@Composable
private fun SurahBand(item: PageItem.SurahBand) {
    val field = Awn.field
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = AwnSpacing.small)
                .clip(AwnShapes.wordBlock)
                .background(field.buttonFill)
                .padding(vertical = AwnSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.chapter.localName(),
            style = AwnTypography.bodyStrong,
            color = field.onButton,
        )
    }
}

@Composable
private fun BasmalahBlock(environment: ListPageEnvironment) {
    Text(
        text = BASMALA_TEXT,
        style =
            AwnTypography.quran.copy(
                fontSize = AwnTypography.quran.fontSize * environment.settings.arabicScale,
                textAlign = TextAlign.Center,
            ),
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.medium),
    )
}

@Suppress("LongMethod") // One block: chip, Arabic, transliteration and translations.
@Composable
private fun AyahItem(block: AyahBlock, environment: ListPageEnvironment, fills: ReaderFills) {
    val settings = environment.settings
    val isSelected = environment.selection == block.ayah
    val isPlaying = environment.playingAyah == block.ayah
    val isSaved = settings.highlightBookmarks && block.ayah in environment.savedAyahs
    val fill =
        when {
            isSelected -> fills.selectedAyah
            isPlaying -> fills.playingAyah
            isSaved -> fills.bookmarkedAyah
            else -> Color.Transparent
        }
    val coordinatesHolder = remember { CoordinatesHolder() }
    val reportLongPress = {
        environment.onAyahLongPress(block.ayah, coordinatesHolder.coordinates?.boundsInWindow())
    }
    Column(
        Modifier.fillMaxWidth()
            .padding(vertical = AwnSpacing.small)
            .onGloballyPositioned { coordinatesHolder.coordinates = it }
            .clip(AwnShapes.control)
            .background(fill)
            .pointerInput(block.ayah) {
                detectTapGestures(
                    onTap = { environment.onTap() },
                    onLongPress = { reportLongPress() },
                )
            }
            .padding(vertical = AwnSpacing.medium, horizontal = AwnSpacing.small),
    ) {
        ReferenceChip(block.ayah)
        val arabicStyle =
            AwnTypography.quran.copy(
                fontSize = AwnTypography.quran.fontSize * settings.arabicScale,
                fontFamily = environment.arabicFamily ?: AwnTypography.quran.fontFamily,
            )
        when (environment.mode) {
            ListMode.Translation ->
                Text(
                    text = block.arabic,
                    style = arabicStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            ListMode.WordByWord ->
                WordsFlow(block, environment, arabicStyle, fills, onAyahLongPress = reportLongPress)
        }
        if (settings.showTransliteration && block.transliteration != null) {
            Text(
                text = block.transliteration,
                style = scaled(AwnTypography.body, settings.translationScale),
                color = Awn.field.buttonFill,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
        block.translations.forEach { translation ->
            val label = translation.rangeLabel?.let { "$it  " }.orEmpty()
            Text(
                text = label + translation.text,
                style = scaled(AwnTypography.body, settings.translationScale),
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

private fun scaled(style: TextStyle, scale: Float): TextStyle =
    style.copy(fontSize = style.fontSize * scale)

/** The "2:255" chip at the start of a block. */
@Composable
private fun ReferenceChip(ayah: AyahRef) {
    Box(
        modifier =
            Modifier.clip(CircleShape)
                .background(AwnColors.ochre)
                .padding(horizontal = AwnSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.ayah_reference, ayah.surah, ayah.ayah),
            style = AwnTypography.small,
            color = AwnColors.ink,
            maxLines = 1,
        )
    }
}

/** The words right to left and wrapping, each with its meaning under it. */
@Composable
private fun WordsFlow(
    block: AyahBlock,
    environment: ListPageEnvironment,
    arabicStyle: TextStyle,
    fills: ReaderFills,
    onAyahLongPress: () -> Unit,
) {
    val settings = environment.settings
    val meaningStyle =
        scaled(AwnTypography.caption, settings.translationScale).copy(textAlign = TextAlign.Center)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            block.words.forEach { word ->
                val isPlayingWord =
                    environment.playingAyah == block.ayah &&
                        environment.playingWordPosition == word.key.wordPosition
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.clip(AwnShapes.wordBlock)
                            .background(if (isPlayingWord) fills.playingWord else Color.Transparent)
                            .pointerInput(word.key) {
                                detectTapGestures(
                                    onTap = { environment.onWordTap(block.ayah, word) },
                                    onLongPress = { onAyahLongPress() },
                                )
                            },
                ) {
                    Text(text = word.arabic, style = arabicStyle)
                    if (settings.showWordByWord && word.meaning != null) {
                        Text(
                            text = word.meaning,
                            style = meaningStyle,
                            modifier = Modifier.widthIn(max = AwnSpacing.giant + AwnSpacing.large),
                        )
                    }
                }
            }
        }
    }
}
