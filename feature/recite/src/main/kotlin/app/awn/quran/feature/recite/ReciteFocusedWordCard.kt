package app.awn.quran.feature.recite

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.player.PlaybackState

/**
 * The mistake card of the word the user tapped on the page, for a bar that has no review button of
 * its own. A tap on a yellow or red word only sets [ReciteController.focusedWord]; the card that
 * shows it is drawn by whoever owns the bar, so the Hifz bar draws it with this. Draws nothing
 * while no word is focused. Closing clears the focus, as in the recite bar.
 */
@Composable
fun ReciteFocusedWordCard(controller: ReciteController, modifier: Modifier = Modifier) {
    val session = controller.session
    val focusedWord by controller.focusedWord.collectAsState()
    if (focusedWord == null) return
    val reviews by session.reviews.collectAsState()
    val problems by session.wordProblems.collectAsState()
    val playback by controller.audioPlayer.state.collectAsState(initial = PlaybackState.Idle)
    val flaggedWords = remember(reviews, problems) { flaggedReviews(reviews, problems) }
    ReciteReviewCardWithWordActions(
        controller = controller,
        reviews = flaggedWords,
        focusedKey = focusedWord,
        isAudioActive = reciteAudioStateOf(playback).isActive(),
        onDismiss = controller::clearFocus,
        modifier = modifier.padding(bottom = AwnSpacing.small),
    )
}
