package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The review card with its word actions wired to [controller]: play says the focused word with the
 * Word audio reciter (stop while any audio plays), and the bookmark saves it to or removes it from
 * the user's own words, as the word card on the page does.
 */
@Composable
internal fun ReciteReviewCardWithWordActions(
    controller: ReciteController,
    reviews: List<WordReview>,
    focusedKey: WordKey?,
    words: List<Word>,
    isAudioActive: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val myWords = controller.myWords
    val reviewedKey = focusedKey ?: reviews.firstOrNull()?.key
    val isSavedFlow =
        remember(myWords, reviewedKey) {
            if (myWords != null && reviewedKey != null) myWords.isAdded(reviewedKey)
            else flowOf(false)
        }
    val isSaved by isSavedFlow.collectAsState(initial = false)
    ReciteReviewCard(
        reviews = reviews,
        focusedKey = focusedKey,
        words = words,
        onFocus = controller::focusWord,
        onListen = { scope.launch { controller.listenToAyah() } },
        onDismiss = onDismiss,
        modifier = modifier,
        isPlaying = isAudioActive,
        onStop = { controller.audioPlayer.stop() },
        isSaved = if (myWords == null) null else isSaved,
        onSave = { key -> scope.launch { myWords?.add(key) } },
        onRemove = { key -> scope.launch { myWords?.remove(key) } },
    )
}
