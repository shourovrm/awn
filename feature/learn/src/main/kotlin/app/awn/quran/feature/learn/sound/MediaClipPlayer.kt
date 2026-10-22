package app.awn.quran.feature.learn.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Plays clips with the platform [MediaPlayer], one at a time. A new clip, [stop] or the end of the
 * calling coroutine ends the one that is playing, and the coroutine that waited for it goes on. A
 * clip that cannot be read ends at once, so a damaged file never leaves "Play all" waiting.
 */
class MediaClipPlayer : ClipPlayer {

    /** Ends the clip that is playing now; null when nothing plays. */
    private var endCurrentClip: (() -> Unit)? = null

    override suspend fun play(file: File, speed: Float) {
        stop()
        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer()
            var hasEnded = false
            val end = {
                if (!hasEnded) {
                    hasEnded = true
                    player.release()
                    endCurrentClip = null
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            endCurrentClip = end
            continuation.invokeOnCancellation { end() }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            player.setOnCompletionListener { end() }
            player.setOnErrorListener { _, _, _ ->
                end()
                true
            }
            try {
                player.setDataSource(file.path)
                player.prepare()
                if (speed != NORMAL_SPEED) player.playbackParams = PlaybackParams().setSpeed(speed)
                player.start()
            } catch (error: IOException) {
                // An unreadable clip counts as played, so a sequence goes on with the next one.
                end()
            } catch (error: IllegalStateException) {
                end()
            }
        }
    }

    override fun stop() {
        endCurrentClip?.invoke()
    }
}
