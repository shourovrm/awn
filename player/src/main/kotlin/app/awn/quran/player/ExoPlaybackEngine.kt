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
@Suppress("TooManyFunctions") // The engine interface plus its player listener helpers.
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

    /**
     * True from the moment the player moved on to the prepared file by itself until the controller
     * asks for that file: [start] then keeps it playing instead of loading it a second time.
     */
    private var movedOnToPreparedFile = false

    override fun start(source: AudioSource, startMs: Long, stopAtMs: Long?) {
        if (continuesPreparedFile(source, startMs, stopAtMs)) {
            movedOnToPreparedFile = false
            // Files already played stay in the list only until the controller has caught up.
            if (player.currentMediaItemIndex > 0)
                player.removeMediaItems(0, player.currentMediaItemIndex)
            player.playWhenReady = true
            return
        }
        movedOnToPreparedFile = false
        player.setMediaItem(mediaItemFor(source), startMs)
        player.prepare()
        player.playWhenReady = true
        if (stopAtMs != null) scheduleStop(stopAtMs)
    }

    override fun prepareNext(source: AudioSource?) {
        // Index 0 is the file playing (or, just after a hand-over, the one before it); everything
        // after the current item is replaced.
        val firstAfterCurrent = player.currentMediaItemIndex + 1
        if (player.mediaItemCount > firstAfterCurrent) {
            player.removeMediaItems(firstAfterCurrent, player.mediaItemCount)
        }
        if (source != null && player.mediaItemCount > 0) player.addMediaItem(mediaItemFor(source))
    }

    @Suppress("ReturnCount") // Guard clauses, one per reason the file cannot be continued.
    private fun continuesPreparedFile(
        source: AudioSource,
        startMs: Long,
        stopAtMs: Long?,
    ): Boolean {
        if (!movedOnToPreparedFile || startMs != 0L || stopAtMs != null) return false
        val playing = player.currentMediaItem ?: return false
        return playing.mediaId == source.localKey &&
            playing.localConfiguration?.uri?.toString() == source.url
    }

    private fun mediaItemFor(source: AudioSource): MediaItem =
        MediaItem.Builder()
            .setMediaId(source.localKey)
            .setUri(source.url)
            .setCustomCacheKey(source.localKey)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(source.title).setArtist(source.artist).build(),
            )
            .build()

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun stop() {
        movedOnToPreparedFile = false
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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The file that was playing ended and the prepared one began with no gap.
            val movedOnByItself =
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (!movedOnByItself) return
            movedOnToPreparedFile = true
            listener?.onEnded()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Our own pause and play use the user-request reason; anything else (audio focus lost,
            // headphones unplugged) happened without the controller knowing.
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                listener?.onPlayWhenReadyChangedExternally(playWhenReady)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // A failure while loading the prepared file must not stop the one that plays: drop the
            // prepared file and carry on. If the playing file is the one that failed, it fails
            // again
            // at once and that second error is reported.
            val preparedFileCount = player.mediaItemCount - (player.currentMediaItemIndex + 1)
            if (preparedFileCount > 0) {
                player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
                player.prepare()
                return
            }
            listener?.onFailed(error.errorCodeName)
        }
    }
}
