package app.awn.quran.feature.read.page

import androidx.compose.runtime.Immutable
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.tracker.WordState

/**
 * Everything drawn on a page besides the text itself. Fills come from `Awn.field` and sit behind
 * each line fragment of the ayah, so an ayah over several lines has a stepped shape.
 *
 * @property selectedAyahs the ayah the user long-pressed, or the range they dragged over.
 * @property playingAyah the ayah being played.
 * @property playingWord the word being played; its fill is drawn on top of the ayah fill.
 * @property bookmarkedAyahs ayahs that carry a bookmark; empty when "Highlight bookmarks" is off.
 * @property wordStates the recitation state of each word; Hidden words are drawn as a block.
 */
@Immutable
data class PageDecorations(
    val selectedAyahs: Set<AyahRef> = emptySet(),
    val playingAyah: AyahRef? = null,
    val playingWord: WordKey? = null,
    val bookmarkedAyahs: Set<AyahRef> = emptySet(),
    val wordStates: (WordKey) -> WordState = { WordState.Neutral },
)
