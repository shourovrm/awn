package app.awn.quran.feature.read.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.awn.quran.core.data.WordKey

/**
 * Recitation started outside the screen (revision from Learn) shows the recite bar, and the page
 * turns with the next word the reciter is expected to say: when that word is on another page, the
 * pager turns to it (the lists scroll to the ayah in `FollowListTargets`). A mistake under review
 * takes its place: the page turns to the focused word and stays there.
 */
@Composable
internal fun FollowRecitation(
    slots: QuranScreenSlots,
    viewModel: QuranViewModel,
    isReciting: Boolean,
) {
    LaunchedEffect(slots.isReciteActive) {
        if (slots.isReciteActive && !viewModel.state.value.isReciting) viewModel.enterReciteMode()
    }
    val expectedWord: WordKey? = slots.reciteCurrentWord()
    val focusedWord: WordKey? = slots.reciteFocusedWord()
    LaunchedEffect(expectedWord, isReciting, focusedWord) {
        // While a mistake is under review the page stays on it, not on the next word to recite.
        if (isReciting && focusedWord == null && expectedWord != null) {
            viewModel.followRecitation(expectedWord)
        }
    }
    LaunchedEffect(focusedWord, isReciting) {
        if (isReciting && focusedWord != null) {
            viewModel.followRecitation(focusedWord, ignoresPause = true)
        }
    }
}
