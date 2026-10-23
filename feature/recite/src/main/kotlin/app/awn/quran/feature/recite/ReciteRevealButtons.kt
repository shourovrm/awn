package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource

/**
 * Show next word and show ayah, the two ways to look at a hidden word without reciting it. The
 * recite bar and the Hifz bar both call this, so the reveal rules live in one place; each bar draws
 * the buttons with [drawButton] and gives them its own test tags.
 */
@Composable
fun ReciteRevealButtons(
    controller: ReciteController,
    wordTag: String,
    ayahTag: String,
    drawButton:
        @Composable
        (icon: Int, description: String, onClick: () -> Unit, tag: String) -> Unit,
) {
    val session = controller.session
    val hint by session.hint.collectAsState()
    val currentWord by session.currentWord.collectAsState()
    val showsFirstLetterFirst by controller.settings.hintEnabled.collectAsState(initial = true)
    // The description says what the next tap does: one tap shows the letter, the next the word.
    val letterIsShown = hint != null && hint?.key == currentWord
    val description =
        if (showsFirstLetterFirst && !letterIsShown) {
            stringResource(R.string.recite_reveal_letter)
        } else {
            stringResource(R.string.recite_reveal_word)
        }
    drawButton(
        R.drawable.ic_recite_reveal_word,
        description,
        { session.revealNextWord(showsFirstLetterFirst) },
        wordTag,
    )
    drawButton(
        R.drawable.ic_recite_reveal_ayah,
        stringResource(R.string.recite_reveal_ayah),
        session::revealAyah,
        ayahTag,
    )
}
