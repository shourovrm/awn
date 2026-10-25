package app.awn.quran.feature.read.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.awn.quran.core.data.WordKey

/**
 * Recitation started outside the screen (revision from Learn) shows the recite bar. The page never
 * turns by itself to the next word the reciter is expected to say: a slip that the tracker reads as
 * a jump must not take the user away, so the recite bar offers "Next page" when the recitation goes
 * on past the page (the lists still scroll to the ayah within the page). A mistake under review is
 * the one exception: the page turns to the focused word and stays there.
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
    val focusedWord: WordKey? = slots.reciteFocusedWord()
    LaunchedEffect(focusedWord, isReciting) {
        if (isReciting && focusedWord != null) {
            viewModel.followRecitation(focusedWord, ignoresPause = true)
        }
    }
}
