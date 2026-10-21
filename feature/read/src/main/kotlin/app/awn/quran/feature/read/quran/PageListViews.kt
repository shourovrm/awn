package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.BASMALA_TEXT
import app.awn.quran.feature.read.settings.ReadSettings

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
    val settings: ReadSettings,
    val arabicFamily: FontFamily?,
    val selection: AyahRange?,
    val playingAyah: AyahRef?,
    /** A State, so a word tick is read only by the block that holds the playing ayah. */
    val playingWordPosition: State<Int?>,
    /** True while the list scrolls with the audio. */
    val isFollowing: Boolean,
    /** Changes when the back-to-playing control asks for the playing ayah again. */
    val followRequest: Int,
    /** The ayah panel covers the lower part of the screen, so a selection may need to scroll. */
    val isPanelOpen: Boolean,
    val savedAyahs: Set<AyahRef>,
    val contentPadding: PaddingValues,
    val onTap: () -> Unit,
    /** The ayah the reader pressed for 500 ms, with its bounds in window coordinates. */
    val onAyahLongPress: (AyahRef, Rect?) -> Unit,
    val onWordTap: (AyahRef, AyahWord) -> Unit,
    val onUserScrolled: () -> Unit,
)

/**
 * One page of the line-by-line view: a list of ayah blocks with a band at each surah. Each block
 * shows the layers the settings tick: Arabic, word by word, transliteration and translations. When
 * [isCurrent], the list scrolls the playing ayah, or a newly selected one, to the top.
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
        LaunchedEffect(content, environment.playingAyah, environment.followRequest) {
            val index = environment.playingAyah?.let(content::indexOf) ?: -1
            if (index >= 0 && environment.isFollowing) listState.showItem(index)
        }
        LaunchedEffect(content, environment.selection?.first, environment.isPanelOpen) {
            val index = environment.selection?.first?.let(content::indexOf) ?: -1
            if (index >= 0) listState.showItem(index)
        }
        // A reader who scrolls the list by hand stops it following the audio.
        LaunchedEffect(listState) {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) environment.onUserScrolled()
            }
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

/** The surah name, centred in the accent colour with no fill, so the list stays quiet. */
@Composable
private fun SurahBand(item: PageItem.SurahBand) {
    Text(
        text = item.chapter.localName(),
        style = AwnTypography.lead,
        color = Awn.field.buttonFill,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.medium),
    )
}

/** The Basmalah in the Quran font, like the Arabic of the ayahs below it. */
@Composable
private fun BasmalahBlock(environment: ListPageEnvironment) {
    Text(
        text = BASMALA_TEXT,
        style =
            AwnTypography.quran.copy(
                fontSize = AwnTypography.quran.fontSize * environment.settings.arabicScale,
                fontFamily = environment.arabicFamily ?: AwnTypography.quran.fontFamily,
                textAlign = TextAlign.Center,
            ),
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.medium),
    )
}

@Suppress("LongMethod") // One block: chip, Arabic, transliteration and translations.
@Composable
private fun AyahItem(block: AyahBlock, environment: ListPageEnvironment, fills: ReaderFills) {
    val settings = environment.settings
    val isSelected = environment.selection?.contains(block.ayah) == true
    val isPlaying = environment.playingAyah == block.ayah
    val playingWord by
        remember(isPlaying, environment.playingWordPosition) {
            derivedStateOf { if (isPlaying) environment.playingWordPosition.value else null }
        }
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
    val selectLabel = stringResource(R.string.ayah_select_action)
    Column(
        Modifier.fillMaxWidth()
            .semantics {
                // The screen-reader form of a long press.
                onLongClick(label = selectLabel) {
                    reportLongPress()
                    true
                }
            }
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
        if (settings.showWordByWord) {
            WordsFlow(
                block = block,
                environment = environment,
                arabicStyle = arabicStyle,
                fills = fills,
                playingWord = playingWord,
                onAyahLongPress = reportLongPress,
            )
        } else if (settings.showArabic) {
            Text(text = block.arabic, style = arabicStyle, modifier = Modifier.fillMaxWidth())
        }
        if (settings.showTransliteration && block.transliteration != null) {
            Text(
                text = block.transliteration,
                style = scaled(AwnTypography.body, settings.transliterationScale),
                color = Awn.field.buttonFill,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
        TranslationTexts(block, settings.translationScale)
    }
}

/** Each translation of the ayah; its name shows above it when more than one is ticked. */
@Composable
private fun TranslationTexts(block: AyahBlock, scale: Float) {
    val namesShow = block.translations.size > 1
    block.translations.forEach { translation ->
        val label = translation.rangeLabel?.let { "$it  " }.orEmpty()
        Column(Modifier.padding(top = AwnSpacing.small)) {
            if (namesShow) {
                Text(
                    text = translation.name,
                    style = AwnTypography.caption,
                    color = Awn.field.buttonFill,
                )
            }
            Text(text = label + translation.text, style = scaled(AwnTypography.body, scale))
        }
    }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

internal fun scaled(style: TextStyle, scale: Float): TextStyle =
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

/**
 * Scrolls so that item [index] is on screen, but only when it is not already fully visible, and
 * then leaves [TOP_MARGIN_SHARE] of the list height above it. An ayah that is already in view never
 * moves, so the list does not jump under a reader who is reading it.
 */
private suspend fun LazyListState.showItem(index: Int) {
    val info = layoutInfo
    val visibleTop = info.beforeContentPadding
    val visibleBottom = info.viewportSize.height - info.afterContentPadding
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item != null && isFullyVisible(item.offset, item.size, visibleTop, visibleBottom)) return
    animateScrollToItem(index, -topMarginFor(visibleBottom - visibleTop))
}
