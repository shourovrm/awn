package app.awn.quran.feature.duas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Listen controls of one dua: whole ayahs play with the reciter chosen in Settings, because the
 * player cannot start or stop inside an ayah.
 */
@Composable
internal fun ListenSection(
    dua: Dua,
    card: DuaCardData?,
    dependencies: DuasDependencies,
    playback: PlaybackState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val audioPlayer = dependencies.audioPlayer
    val options by audioPlayer.options.collectAsState()
    val prompt by audioPlayer.downloadPrompt.collectAsState()
    // A dua is a few ayahs, so a question about downloading is answered by streaming.
    LaunchedEffect(prompt) {
        if (prompt != null) audioPlayer.answerDownloadPrompt(download = false)
    }
    val reciterName by rememberReciterName(dependencies)
    ListenPanel(
        dua = dua,
        reciterName = reciterName,
        playback = playback,
        options = options,
        wordCountOfAyah = { ayah: AyahRef ->
            card?.words.orEmpty().count { it.word.key.ayah == ayah.ayah }
        },
        actions =
            ListenPanelActions(
                onPlay = {
                    scope.launch {
                        val reciterId = dependencies.selectedReciterId.first()
                        audioPlayer.play(reciterId, dua.ayahs.first(), dua.ayahs.last())
                    }
                },
                onStop = audioPlayer::stop,
                onCycleRepeat = {
                    audioPlayer.setRepeat(nextRepeatCount(options.repeatEachAyah) - 1)
                },
                onCycleSpeed = {
                    audioPlayer.setOptions(options.copy(speed = nextSpeed(options.speed)))
                },
            ),
        modifier = modifier,
    )
}

@Composable
private fun rememberReciterName(dependencies: DuasDependencies): State<String> =
    produceState(initialValue = "", dependencies) {
        dependencies.selectedReciterId.collectLatest { reciterId ->
            val reciters = dependencies.reciters.reciters()
            value = reciters.firstOrNull { it.id == reciterId }?.name.orEmpty()
        }
    }
