package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the Quran screen calls to start audio. Playback uses the selected reciter and the repeat and
 * speed settings the player holds; missing audio is downloaded first when [AudioPlayer] is a
 * [app.awn.quran.player.DownloadingAudioPlayer].
 *
 * Choosing another reciter while audio plays restarts the same range with that reciter, from the
 * ayah now playing. While paused or idle, a reciter change starts nothing.
 *
 * [scope] should outlive the screen, so that a request made just before leaving is not dropped.
 */
class AudioController(
    private val player: AudioPlayer,
    private val settings: ListenSettings,
    private val scope: CoroutineScope,
) {

    /** The end of the range in progress, kept so a reciter change can play the same range on. */
    private var rangeEnd: AyahRef? = null

    init {
        scope.launch {
            // distinctUntilChanged comes before drop: dropping first would let a repeat of the
            // first value through as a change.
            settings.selectedReciterId.distinctUntilChanged().drop(1).collect { reciterId ->
                continueRangeWithReciter(reciterId)
            }
        }
    }

    /** Plays from [ayah] to the end of its surah ("play from here" on a selected ayah). */
    fun playFrom(ayah: AyahRef) = playRange(ayah, to = null)

    /** Plays from [from] up to and including [to]; a null [to] means the end of the surah. */
    fun playRange(from: AyahRef, to: AyahRef?) {
        rangeEnd = to
        scope.launch { player.play(settings.selectedReciterId.first(), from, to) }
    }

    /** Plays the rest of the range with [reciterId], starting at the ayah that is playing now. */
    private suspend fun continueRangeWithReciter(reciterId: String) {
        val playerState = player.state.first()
        if (playerState !is PlaybackState.Playing) return
        player.play(reciterId, playerState.ayah, rangeEnd)
    }
}
