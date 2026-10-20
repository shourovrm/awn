package app.awn.quran.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
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
        createNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.media_channel_name)
                .build(),
        )
        mediaSession = MediaSession.Builder(this, audioPlayer.sessionPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Swiping the app away stops playback unless the user turned that off. The default of
     * [MediaSessionService] keeps a playing session alive, which surprises someone who closed the
     * app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val audioPlayer = ExoAudioPlayer.active
        if (audioPlayer != null && audioPlayer.options.value.stopOnTaskRemoved) {
            audioPlayer.stop()
            stopSelf()
        } else {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /** A quiet channel (no sound, no pop-up) whose notification shows on the lock screen. */
    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "audio_playback"
    }
}
