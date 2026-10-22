package app.awn.quran.feature.recite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.RemovedMyWord
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.UndoMessage
import app.awn.quran.core.ui.theme.AwnSpacing
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
    // Held only until the message goes away: it is what Undo puts back.
    var removedWord by remember { mutableStateOf<RemovedMyWord?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        removedWord?.let { removed ->
            key(removed) {
                UndoMessage(
                    message = stringResource(R.string.recite_word_removed),
                    actionLabel = stringResource(R.string.recite_word_undo),
                    onAction = {
                        scope.launch { removed.restore() }
                        removedWord = null
                    },
                    onTimeout = { removedWord = null },
                )
            }
        }
        ReciteReviewCard(
            reviews = reviews,
            focusedKey = focusedKey,
            words = words,
            onFocus = controller::focusWord,
            onListen = { scope.launch { controller.listenToAyah() } },
            onDismiss = onDismiss,
            isPlaying = isAudioActive,
            onStop = { controller.audioPlayer.stop() },
            isSaved = if (myWords == null) null else isSaved,
            onSave = { key -> scope.launch { myWords?.add(key) } },
            onRemove = { wordKey -> scope.launch { removedWord = myWords?.remove(wordKey) } },
        )
    }
}
