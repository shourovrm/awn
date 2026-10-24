package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.translationNameWithNote
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.BASMALA_TEXT
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.page.rememberFontsLoaded
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState

/** The fills behind ayahs and words in the list views, taken from the colour mode. */
class ReaderFills(
    val selectedAyah: Color,
    val playingAyah: Color,
    val playingWord: Color,
    val bookmarkedAyah: Color,
)

@Composable
fun readerFills(): ReaderFills {
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
    /** Page fonts of the glyph prints; each word asks it for the family of its own page. */
    val fontCache: PageFontCache,
    /** The recite state of each word while reciting; empty otherwise. */
    val wordStates: Map<WordKey, WordState>,
    /** Words the user knows or is learning, filled when "Show my words" is on. */
    val myWordMarks: Map<WordKey, MyWordMark> = emptyMap(),
    /** The first letter shown inside the block of a Hidden word. */
    val hintLetters: Map<WordKey, String> = emptyMap(),
    val isReciting: Boolean,
    /**
     * The ayah of the word the reciter is on; the list keeps it in view. Null when not reciting.
     */
    val recitedAyah: AyahRef? = null,
    /** The flagged word under review, drawn with a ring. */
    val focusedWord: WordKey? = null,
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
    /** The tapped word and where it is, in window coordinates. */
    val onWordTap: (AyahRef, AyahWord, Rect?) -> Unit,
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
    val fontPages = remember(content) { content?.fontPages.orEmpty() }
    val areFontsReady =
        rememberFontsLoaded(
            environment.fontCache,
            environment.settings.script,
            fontPages,
            isNight = !Awn.field.isLight,
        )
    if (content != null && isCurrent) {
        FollowListTargets(content, environment, listState, areFontsReady)
    }
    LazyColumn(
        state = listState,
        modifier =
            modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { environment.onTap() })
            },
        contentPadding = environment.contentPadding,
    ) {
        items(
            items = if (areFontsReady) content?.items.orEmpty() else emptyList(),
            key = { itemKey(it) },
        ) { item ->
            when (item) {
                is PageItem.SurahBand -> SurahBand(item)
                is PageItem.Basmalah -> BasmalahBlock(environment)
                is PageItem.Ayah -> AyahItem(item.block, environment, fills)
            }
        }
        // A list stops scrolling when its last item meets the bottom, so the last ayahs of a
        // surah would stay under the recite bar however far the reciter has got. This space lets
        // the ayah being recited rise to the top.
        if (environment.isReciting && areFontsReady) {
            item(key = TAIL_SPACE_KEY) { Spacer(Modifier.fillParentMaxHeight()) }
        }
    }
}

private const val TAIL_SPACE_KEY = "recite-tail-space"

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
        if (usesWordCells(block, settings, environment)) {
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
                    text = translationNameWithNote(translation.name, translation.packId),
                    style = AwnTypography.caption,
                    color = Awn.field.buttonFill,
                )
            }
            Text(text = label + translation.text, style = scaled(AwnTypography.body, scale))
        }
    }
}

internal class CoordinatesHolder {
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
