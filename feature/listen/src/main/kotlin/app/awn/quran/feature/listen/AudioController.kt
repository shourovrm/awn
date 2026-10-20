package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the Quran screen calls to start audio. Playback uses the selected reciter and the repeat and
 * speed settings the player holds; missing audio is downloaded first when [AudioPlayer] is a
 * [app.awn.quran.player.DownloadingAudioPlayer].
 *
 * [scope] should outlive the screen, so that a request made just before leaving is not dropped.
 */
class AudioController(
    private val player: AudioPlayer,
    private val settings: ListenSettings,
    private val scope: CoroutineScope,
) {

    /** Plays from [ayah] to the end of its surah ("play from here" on a selected ayah). */
    fun playFrom(ayah: AyahRef) = playRange(ayah, to = null)

    /** Plays from [from] up to and including [to]; a null [to] means the end of the surah. */
    fun playRange(from: AyahRef, to: AyahRef?) {
        scope.launch { player.play(settings.selectedReciterId.first(), from, to) }
    }
}
