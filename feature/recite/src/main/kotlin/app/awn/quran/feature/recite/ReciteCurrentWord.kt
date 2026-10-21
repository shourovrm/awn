package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.awn.quran.core.data.WordKey

/**
 * The next word the reciter is expected to say, null when not reciting or when the loaded words are
 * done. The Quran screen turns to the page of this word, which is finer than the ayah: an ayah that
 * runs over a page break turns the page at the word where the break falls.
 */
@Composable
fun rememberReciteCurrentWord(controller: ReciteController): WordKey? {
    val word by controller.session.currentWord.collectAsState()
    return word
}
