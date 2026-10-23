package app.awn.quran.feature.read.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.WordKey

/** Every word printed on a Mushaf page, in reading order. */
internal fun wordKeysOf(layout: PageLayout): List<WordKey> =
    layout.lines
        .flatMap { line -> line.items }
        .filterIsInstance<LineItem.WordItem>()
        .map { item -> item.word.key }

/** Every word a list page shows, in reading order; empty while the page is not loaded. */
internal fun wordKeysOf(content: PageContent?): List<WordKey> =
    content
        ?.items
        .orEmpty()
        .filterIsInstance<PageItem.Ayah>()
        .flatMap { item -> item.block.words }
        .map { word -> word.key }

/**
 * How well the user knows each of [keys], while [isShown] ("Show my words") is on and a word list
 * is present. Empty otherwise, so a page without the setting draws exactly as before.
 */
@Composable
internal fun rememberMyWordMarks(
    myWords: MyWords?,
    keys: List<WordKey>,
    isShown: Boolean,
): State<Map<WordKey, MyWordMark>> =
    produceState(emptyMap<WordKey, MyWordMark>(), myWords, keys, isShown) {
        if (!isShown || myWords == null || keys.isEmpty()) {
            value = emptyMap()
        } else {
            myWords.marks(keys).collect { marks -> value = marks }
        }
    }
