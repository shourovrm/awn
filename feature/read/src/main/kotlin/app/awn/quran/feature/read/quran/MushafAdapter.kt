package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageDecorations
import app.awn.quran.feature.read.page.slotCountOf
import app.awn.quran.tracker.WordState

private const val SLOTS_PER_PAGE = 15

/** The vertical extent of an ayah on a Mushaf page, in pixels from the top of the page. */
internal data class AyahSpan(val top: Float, val bottom: Float)

/** The page slots (1..15) that hold words or the end sign of [ayah], or null if none do. */
internal fun ayahSlotRange(layout: PageLayout, ayah: AyahRef): IntRange? {
    val slots =
        layout.lines
            .filter { line ->
                line.items.any { item ->
                    when (item) {
                        is LineItem.WordItem ->
                            item.word.key.surah == ayah.surah && item.word.key.ayah == ayah.ayah
                        is LineItem.AyahEnd -> item.ayah == ayah
                        is LineItem.SurahHeader,
                        is LineItem.Basmala -> false
                    }
                }
            }
            .map { it.line }
    return if (slots.isEmpty()) null else slots.min()..slots.max()
}

/**
 * Where [ayah] is on a page drawn [pageHeight] pixels tall. The page draws its slots at pageHeight
 * / 15 each, centred vertically when it has fewer than 15 (pages 1 and 2).
 */
internal fun ayahSpan(layout: PageLayout, ayah: AyahRef, pageHeight: Float): AyahSpan? {
    val range = ayahSlotRange(layout, ayah) ?: return null
    val slotHeight = pageHeight / SLOTS_PER_PAGE
    val topOfFirstSlot = (pageHeight - slotCountOf(layout) * slotHeight) / 2f
    return AyahSpan(
        top = topOfFirstSlot + (range.first - 1) * slotHeight,
        bottom = topOfFirstSlot + range.last * slotHeight,
    )
}

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

/**
 * The one place that calls [MushafPage]: maps the screen's highlights to [PageDecorations]. A tap
 * anywhere on the page toggles the bars, a long press on a word selects its ayah.
 */
@Composable
internal fun MushafPageHost(
    layout: PageLayout,
    chapters: List<Chapter>,
    fontFamily: FontFamily?,
    plainFamily: FontFamily?,
    highlights: MushafHighlights,
    wordStates: Map<WordKey, WordState>,
    showPageInfo: Boolean,
    onTap: () -> Unit,
    onAyahPressed: (AyahRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        MushafPage(
            layout = layout,
            chapters = chapters,
            fontFamily = fontFamily,
            plainFamily = plainFamily,
            decorations =
                PageDecorations(
                    selectedAyah = highlights.selectedAyah,
                    playingAyah = highlights.playingAyah,
                    playingWord = highlights.playingWord,
                    bookmarkedAyahs = highlights.bookmarkedAyahs,
                    wordStates = { key -> wordStates[key] ?: WordState.Neutral },
                ),
            showPageInfo = showPageInfo,
            onWordClick = { onTap() },
            onAyahLongPress = { ayah, _ -> onAyahPressed(ayah) },
        )
    }
}

/** What the Mushaf page should mark; mirrors the fields of the page agent's PageDecorations. */
internal data class MushafHighlights(
    val selectedAyah: AyahRef? = null,
    val playingAyah: AyahRef? = null,
    val playingWord: WordKey? = null,
    val bookmarkedAyahs: Set<AyahRef> = emptySet(),
)
