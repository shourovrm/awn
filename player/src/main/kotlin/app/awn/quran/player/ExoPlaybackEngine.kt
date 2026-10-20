package app.awn.quran.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import okhttp3.OkHttpClient

/**
 * A [PlaybackEngine] on Media3 ExoPlayer. It plays one file at a time and reads downloaded copies
 * first (see [LocalFirstDataSource]). [player] is the instance the media session wraps.
 */
@UnstableApi
class ExoPlaybackEngine(
    context: Context,
    httpClient: OkHttpClient,
    store: LocalAudioStore,
) : PlaybackEngine {

    override var listener: PlaybackEngine.Listener? = null

    val player: ExoPlayer

    init {
        val streamingFactory = OkHttpDataSource.Factory(httpClient)
        val localFirstFactory = DataSource.Factory {
            LocalFirstDataSource(store, FileDataSource(), streamingFactory.createDataSource())
        }
        // Without a seek table, treat the file as constant bit rate so a word seek lands within
        // one frame instead of at the start. Quranic audio files are constant bit rate.
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        player =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(localFirstFactory, extractors))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        player.addListener(PlayerEvents())
    }

    override fun start(source: AudioSource, startMs: Long, stopAtMs: Long?) {
        val mediaItem =
            MediaItem.Builder()
                .setUri(source.url)
                .setCustomCacheKey(source.localKey)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(source.title).setArtist(source.artist).build(),
                )
                .build()
        player.setMediaItem(mediaItem, startMs)
        player.prepare()
        player.playWhenReady = true
        if (stopAtMs != null) scheduleStop(stopAtMs)
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun positionMs(): Long = player.currentPosition

    override fun durationMs(): Long? = player.duration.takeIf { it != C.TIME_UNSET }

    /** Delivers the stop on the playback thread at the exact sample, not at the next poll. */
    private fun scheduleStop(stopAtMs: Long) {
        player
            .createMessage { _, _ ->
                player.pause()
                listener?.onEnded()
            }
            .setLooper(Looper.getMainLooper())
            .setPosition(stopAtMs)
            .setDeleteAfterDelivery(true)
            .send()
    }

    private inner class PlayerEvents : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) listener?.onEnded()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Our own pause and play use the user-request reason; anything else (audio focus lost,
            // headphones unplugged) happened without the controller knowing.
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                listener?.onPlayWhenReadyChangedExternally(playWhenReady)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onFailed(error.errorCodeName)
        }
    }
}
