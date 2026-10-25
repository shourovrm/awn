package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageBarInsets
import app.awn.quran.feature.read.page.PageDecorations
import app.awn.quran.feature.read.page.SelectionBounds
import app.awn.quran.tracker.WordState

/**
 * Lays [content] out at [fullHeightPx] and moves it up by [shiftPx], clipped by the parent. The
 * page is cropped by the ayah panel, never scaled.
 */
internal fun Modifier.fixedHeightShifted(fullHeightPx: Int, shiftPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        val placeable =
            measurable.measure(
                constraints.copy(minHeight = fullHeightPx, maxHeight = fullHeightPx),
            )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -shiftPx()) }
    }

/** Every ayah that has a word or an end sign on the page. */
internal fun ayahsOnLayout(layout: PageLayout): Set<AyahRef> =
    layout.lines
        .flatMap { line -> line.items }
        .mapNotNull { item ->
            when (item) {
                is LineItem.WordItem -> AyahRef(item.word.key.surah, item.word.key.ayah)
                is LineItem.AyahEnd -> item.ayah
                is LineItem.SurahHeader,
                is LineItem.Basmala -> null
            }
        }
        .toSet()

/**
 * What the screen knows about marking the ayahs, before it is narrowed to one page by
 * [highlightsFor]. [playingWordPosition] is a State so that a word tick is read only by the page
 * that holds the playing ayah.
 */
@Immutable
internal data class ScreenMarks(
    val selection: AyahRange?,
    val playingAyah: AyahRef?,
    val playingWordPosition: State<Int?>,
    val bookmarkedAyahs: Set<AyahRef>,
)

/**
 * The marks that fall on a page holding [pageAyahs]. Two pages get equal results unless a mark
 * changed on that page, so a playback tick on one page does not recompose the others.
 */
internal fun highlightsFor(
    pageAyahs: Set<AyahRef>,
    selection: AyahRange?,
    playingAyah: AyahRef?,
    playingWordPosition: Int?,
    bookmarkedAyahs: Set<AyahRef>,
): MushafHighlights {
    val playingHere = playingAyah?.takeIf { it in pageAyahs }
    return MushafHighlights(
        selectedAyahs = pageAyahs.filterTo(mutableSetOf()) { selection?.contains(it) == true },
        playingAyah = playingHere,
        playingWord =
            if (playingHere != null && playingWordPosition != null) {
                WordKey(playingHere.surah, playingHere.ayah, playingWordPosition)
            } else {
                null
            },
        bookmarkedAyahs = pageAyahs.filterTo(mutableSetOf()) { it in bookmarkedAyahs },
    )
}

/**
 * A Mushaf page with the marks that fall on it. The word position is read through a derived state
 * that is constant on pages without the playing ayah.
 */
@Composable
internal fun MushafPageItem(
    layout: PageLayout,
    marks: ScreenMarks,
    host: MushafHost,
    modifier: Modifier = Modifier,
) {
    val pageAyahs = remember(layout) { ayahsOnLayout(layout) }
    val isPlayingHere = marks.playingAyah != null && marks.playingAyah in pageAyahs
    val wordPosition by
        remember(isPlayingHere, marks.playingWordPosition) {
            derivedStateOf { if (isPlayingHere) marks.playingWordPosition.value else null }
        }
    val highlights =
        highlightsFor(
            pageAyahs = pageAyahs,
            selection = marks.selection,
            playingAyah = marks.playingAyah,
            playingWordPosition = wordPosition,
            bookmarkedAyahs = marks.bookmarkedAyahs,
        )
    MushafPageHost(layout, highlights, host, modifier)
}

/** What a Mushaf page needs besides its marks: text, fonts, recite colours and the actions. */
@Immutable
internal data class MushafHost(
    val chapters: List<Chapter>,
    val fontFamily: FontFamily?,
    val plainFamily: FontFamily?,
    val wordStates: Map<WordKey, WordState>,
    /** Words the user knows or is learning, filled when "Show my words" is on. */
    val myWordMarks: Map<WordKey, MyWordMark> = emptyMap(),
    /** The first letter shown inside the block of a Hidden word. */
    val hintLetters: Map<WordKey, String> = emptyMap(),
    /** Words said wrong and then right while reciting; drawn with an outline. */
    val correctedWords: Set<WordKey> = emptySet(),
    /** A tap on a word, with the word's place in the screen body. */
    val onWordTap: (WordKey, Rect) -> Unit,
    val showPageInfo: Boolean,
    /** The size of the letters compared with the default (1 is the default, see `planPage`). */
    val sizeFactor: Float,
    /** The room the bars over the top and bottom of the screen take; no text is drawn there. */
    val barInsets: PageBarInsets,
    /** True for a page built ahead of the swipe, which draws its rows a few per frame. */
    val revealGradually: Boolean,
    val onTap: () -> Unit,
    val onAyahPressed: (AyahRef) -> Unit,
    val onRangeDrag: (AyahRange) -> Unit,
    val onRangeEnd: () -> Unit,
    val onSelectionBounds: (SelectionBounds?) -> Unit,
    /** The flagged word under review, drawn with a ring. */
    val focusedWord: WordKey? = null,
    /** The word the screen keeps above the recite bar, and where it reports that word's place. */
    val trackedWord: WordKey? = null,
    val onTrackedWordBounds: (Rect?) -> Unit = {},
)

/**
 * The one place that calls [MushafPage]: maps the screen's highlights to [PageDecorations]. A tap
 * anywhere on the page toggles the bars, a long press on a word selects its ayah.
 */
@Composable
internal fun MushafPageHost(
    layout: PageLayout,
    highlights: MushafHighlights,
    host: MushafHost,
    modifier: Modifier = Modifier,
) {
    val wordStates = host.wordStates
    // Remembered, so equal marks give an equal PageDecorations and the page can skip recomposing.
    val decorations =
        remember(
            highlights,
            wordStates,
            host.myWordMarks,
            host.hintLetters,
            host.focusedWord,
            host.correctedWords,
        ) {
            PageDecorations(
                selectedAyahs = highlights.selectedAyahs,
                playingAyah = highlights.playingAyah,
                playingWord = highlights.playingWord,
                bookmarkedAyahs = highlights.bookmarkedAyahs,
                focusedWord = host.focusedWord,
                wordStates = { key -> wordStates[key] ?: WordState.Neutral },
                myWordMarks = host.myWordMarks,
                hintLetter = { key -> host.hintLetters[key] },
                correctedWords = host.correctedWords,
            )
        }
    Box(modifier.fillMaxSize()) {
        MushafPage(
            layout = layout,
            chapters = host.chapters,
            fontFamily = host.fontFamily,
            plainFamily = host.plainFamily,
            decorations = decorations,
            onWordTap = host.onWordTap,
            showPageInfo = host.showPageInfo,
            sizeFactor = host.sizeFactor,
            barInsets = host.barInsets,
            revealGradually = host.revealGradually,
            onTap = host.onTap,
            onAyahLongPress = host.onAyahPressed,
            onRangeDrag = host.onRangeDrag,
            onRangeEnd = host.onRangeEnd,
            onSelectionBounds = host.onSelectionBounds,
            trackedWord = host.trackedWord,
            onTrackedWordBounds = host.onTrackedWordBounds,
        )
    }
}

/** What the Mushaf page should mark; mirrors the fields of the page agent's PageDecorations. */
@Immutable
internal data class MushafHighlights(
    val selectedAyahs: Set<AyahRef> = emptySet(),
    val playingAyah: AyahRef? = null,
    val playingWord: WordKey? = null,
    val bookmarkedAyahs: Set<AyahRef> = emptySet(),
)
