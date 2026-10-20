package app.awn.quran.feature.read.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Recitation started outside the screen (revision from Learn) shows the recite bar, and the page
 * turns with the ayah being recited.
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
    val recitedAyah = slots.reciteCurrentAyah()
    LaunchedEffect(recitedAyah, isReciting) {
        if (isReciting && recitedAyah != null) viewModel.followRecitation(recitedAyah)
    }
}
