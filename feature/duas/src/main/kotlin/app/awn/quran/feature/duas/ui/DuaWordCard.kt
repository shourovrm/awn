package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.read.quran.MyWordState
import app.awn.quran.feature.read.quran.WordPopup
import app.awn.quran.feature.read.quran.WordPopupCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The Mushaf's green word card for a tapped word of a dua card. A tap anywhere else closes it. Play
 * says the word in the voice chosen in Settings, and Save word puts it in the user's words.
 */
@Composable
internal fun DuaWordCard(
    target: WordCardTarget,
    dependencies: DuasDependencies,
    rootOrigin: Offset,
    topClearancePx: Float,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val key = target.word.key
    var isPlaying by remember(key) { mutableStateOf(false) }
    var playJob by remember(key) { mutableStateOf<Job?>(null) }
    val myWords = dependencies.myWords
    val isAdded by
        remember(myWords, key) { myWords?.isAdded(key) ?: flowOf(false) }
            .collectAsState(initial = false)
    // The word keeps sounding after the card is gone otherwise.
    DisposableEffect(key) { onDispose { if (isPlaying) dependencies.audioPlayer.stop() } }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        WordPopupCard(
            popup = WordPopup(ayah = target.ayah, word = target.word, canPlay = true),
            anchor = target.anchor?.translate(-rootOrigin.x, -rootOrigin.y),
            topClearancePx = topClearancePx,
            onDismiss = onDismiss,
            isPlaying = isPlaying,
            onPlay = {
                isPlaying = true
                playJob = scope.launch {
                    try {
                        dependencies.playWord(key)
                    } finally {
                        isPlaying = false
                    }
                }
            },
            onStop = {
                playJob?.cancel()
                dependencies.audioPlayer.stop()
                isPlaying = false
            },
            myWordState =
                when {
                    myWords == null -> null
                    isAdded -> MyWordState.Added
                    else -> MyWordState.NotAdded
                },
            onAddToMyWords = { myWords?.let { scope.launch { it.add(key) } } },
            onRemoveFromMyWords = { myWords?.let { scope.launch { it.remove(key) } } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
