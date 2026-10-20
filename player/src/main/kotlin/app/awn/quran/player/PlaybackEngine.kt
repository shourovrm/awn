package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.WordTiming

/**
 * One audio file to play. [localKey] names the downloaded copy (see [LocalAudioStore]); when that
 * copy exists the engine reads it instead of [url].
 */
data class AudioSource(
    val url: String,
    val localKey: String,
    val title: String,
    val artist: String,
)

/**
 * The part of a media player that [PlaybackController] needs. [ExoPlaybackEngine] is the real one;
 * tests use a fake that moves time by hand. All calls and callbacks happen on the main thread.
 */
interface PlaybackEngine {

    var listener: Listener?

    /**
     * Loads [source], seeks to [startMs] and starts playing. When [stopAtMs] is set, the engine
     * pauses at that position and calls [Listener.onEnded]; otherwise it calls it at the end of the
     * file.
     */
    fun start(source: AudioSource, startMs: Long, stopAtMs: Long?)

    fun pause()

    fun resume()

    fun stop()

    fun positionMs(): Long

    fun durationMs(): Long?

    interface Listener {
        fun onEnded()

        fun onFailed(reason: String)

        /** The device paused or resumed playback on its own, for example on a phone call. */
        fun onPlayWhenReadyChangedExternally(playWhenReady: Boolean)
    }
}

/** An ayah's file, its length if known and its word timings (empty if none). */
data class ResolvedAudio(
    val source: AudioSource,
    val durationMs: Long?,
    val words: List<WordTiming>,
    /** True when [source] is the file of the whole surah, not of one ayah. */
    val coversWholeSurah: Boolean,
)

fun interface AudioResolver {

    /** Returns the audio to play for [ayah], or null when the reciter or the file is unknown. */
    suspend fun resolve(reciterId: String, ayah: AyahRef): ResolvedAudio?
}

/**
 * Chooses the file for an ayah. The URL in the timing pack comes first because the word timings
 * were made against that exact file. Without it, the reciter's own ayah pattern is used, and for a
 * reciter with no ayah files (Raad al-Kurdi) the surah file.
 */
class ReciterAudioResolver(
    private val reciters: ReciterRepository,
    private val store: LocalAudioStore,
) : AudioResolver {

    override suspend fun resolve(reciterId: String, ayah: AyahRef): ResolvedAudio? {
        val reciter = reciters.reciters().firstOrNull { it.id == reciterId } ?: return null
        val timedAudio = reciters.ayahAudio(reciterId, ayah)
        val ayahUrlPattern = reciter.ayahUrlPattern
        val ayahKey = store.ayahKey(reciterId, ayah)
        return when {
            timedAudio != null ->
                ResolvedAudio(
                    source = source(reciter, timedAudio.url, ayahKey, ayah),
                    durationMs = timedAudio.durationMs?.toLong(),
                    words = timedAudio.words,
                    coversWholeSurah = false,
                )
            ayahUrlPattern != null ->
                ResolvedAudio(
                    source =
                        source(reciter, AudioUrls.ayahUrl(ayahUrlPattern, ayah), ayahKey, ayah),
                    durationMs = null,
                    words = emptyList(),
                    coversWholeSurah = false,
                )
            else ->
                ResolvedAudio(
                    source =
                        source(
                            reciter,
                            AudioUrls.surahUrl(reciter.surahUrlPattern, ayah.surah),
                            store.surahKey(reciterId, ayah.surah),
                            ayah,
                        ),
                    durationMs = null,
                    words = emptyList(),
                    coversWholeSurah = true,
                )
        }
    }

    private fun source(reciter: Reciter, url: String, key: String, ayah: AyahRef) =
        AudioSource(url = url, localKey = key, title = ayah.toString(), artist = reciter.name)
}
