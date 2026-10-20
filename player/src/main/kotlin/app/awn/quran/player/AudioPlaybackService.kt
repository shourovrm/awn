package app.awn.quran.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service that holds the media session of [ExoAudioPlayer], so playback continues with
 * the screen off and shows the media notification. It owns no playback logic.
 */
@UnstableApi
class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioPlayer = ExoAudioPlayer.active
        if (audioPlayer == null) {
            // The process was recreated without the app creating its player; nothing to hold.
            stopSelf()
            return
        }
        mediaSession = MediaSession.Builder(this, audioPlayer.sessionPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
