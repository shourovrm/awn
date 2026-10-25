package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.awn.quran.core.data.WordKey

/**
 * The word the reciter is saying now, null when not reciting or when the loaded words are done. It
 * moves back while the user repeats words already said. The Quran screen keeps it in view on the
 * page shown; it never turns the page to it.
 */
@Composable
fun rememberReciteCurrentWord(controller: ReciteController): WordKey? {
    val word by controller.session.currentWord.collectAsState()
    return word
}

/**
 * The flagged word the user is reviewing in the review card, null when none. The Quran screen turns
 * to its page and rings it.
 */
@Composable
fun rememberReciteFocusedWord(controller: ReciteController): WordKey? {
    val word by controller.focusedWord.collectAsState()
    return word
}
