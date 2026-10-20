package app.awn.quran.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The app's one [AudioPlayer]: Media3 ExoPlayer behind a foreground [AudioPlaybackService] with a
 * media notification. Create it once, on startup, with the application context; the service finds
 * it through [active].
 *
 * Downloads written by [OkHttpAudioDownloads] with the same [audioRoot] play without a network.
 */
@OptIn(UnstableApi::class)
class ExoAudioPlayer(
    context: Context,
    reciters: ReciterRepository,
    quranText: QuranTextRepository,
    httpClient: OkHttpClient = OkHttpClient(),
    audioRoot: File = File(context.filesDir, DEFAULT_AUDIO_DIRECTORY),
) : AudioPlayer {

    private val appContext = context.applicationContext
    private val store = LocalAudioStore(audioRoot)
    private val engine = ExoPlaybackEngine(context, httpClient, store)
    private val controller =
        PlaybackController(
            engine = engine,
            resolver = ReciterAudioResolver(reciters, store),
            playlistBuilder = PlaylistBuilder(quranText),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )

    /** Keeps the binding that starts and holds [AudioPlaybackService]. */
    private var serviceConnection: Any? = null

    override val state: StateFlow<PlaybackState> = controller.state

    /** The player the media session shows: play, pause and stop go through the controller. */
    val sessionPlayer: Player = SessionPlayer(engine.player)

    init {
        active = this
    }

    override suspend fun play(reciterId: String, from: AyahRef, to: AyahRef?) {
        withContext(Dispatchers.Main.immediate) {
            ensureServiceRunning()
            controller.play(reciterId, from, to)
        }
    }

    override suspend fun playWord(reciterId: String, ayah: AyahRef, position: Int) {
        withContext(Dispatchers.Main.immediate) {
            ensureServiceRunning()
            controller.playWord(reciterId, ayah, position)
        }
    }

    override fun pause() = controller.pause()

    override fun resume() = controller.resume()

    override fun stop() = controller.stop()

    override fun setRepeat(times: Int) = controller.setRepeat(times)

    /**
     * Connecting a media controller to the service starts it, and Media3 then promotes it to a
     * foreground service while audio plays.
     */
    private fun ensureServiceRunning() {
        if (serviceConnection != null) return
        val token =
            SessionToken(appContext, ComponentName(appContext, AudioPlaybackService::class.java))
        serviceConnection = MediaController.Builder(appContext, token).buildAsync()
    }

    private inner class SessionPlayer(wrapped: Player) : ForwardingPlayer(wrapped) {

        private val sessionCommands =
            Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_GET_TIMELINE,
                )
                .build()

        override fun getAvailableCommands(): Player.Commands = sessionCommands

        override fun isCommandAvailable(command: Int): Boolean = sessionCommands.contains(command)

        override fun play() = controller.resume()

        override fun pause() = controller.pause()

        override fun stop() = controller.stop()

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) controller.resume() else controller.pause()
        }
    }

    companion object {
        const val DEFAULT_AUDIO_DIRECTORY = "audio"

        /** The instance [AudioPlaybackService] attaches its session to; set by the constructor. */
        // Holds only the application context, so it cannot leak an activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var active: ExoAudioPlayer? = null
            private set
    }
}
