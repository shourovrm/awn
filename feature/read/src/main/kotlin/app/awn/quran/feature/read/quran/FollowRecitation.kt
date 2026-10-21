package app.awn.quran.feature.read.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.awn.quran.core.data.WordKey

/**
 * Recitation started outside the screen (revision from Learn) shows the recite bar, and the page
 * turns with the next word the reciter is expected to say: when that word is on another page, the
 * pager turns to it (the lists scroll to the ayah in `FollowListTargets`).
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
    LaunchedEffect(expectedWord, isReciting) {
        if (isReciting && expectedWord != null) viewModel.followRecitation(expectedWord)
    }
}
