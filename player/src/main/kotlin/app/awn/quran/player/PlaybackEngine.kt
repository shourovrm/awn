package app.awn.quran.player

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.WordTiming
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /**
     * Prepares [source] to follow the file now playing, so it starts without a gap when that file
     * ends; null drops the prepared file. Has no effect on what plays now. When the engine moves on
     * by itself it calls [Listener.onEnded] for the finished file, and the next [start] of the
     * prepared file continues it instead of loading it again.
     */
    fun prepareNext(source: AudioSource?)

    fun pause()

    fun resume()

    /** Plays at [speed] times the normal rate without changing the pitch. */
    fun setSpeed(speed: Float)

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

/**
 * An ayah's file, its length if known and its word timings (empty if none). All times are in
 * milliseconds from the start of the file the engine plays, which for a downloaded clip of a surah
 * file is the start of the clip.
 */
data class ResolvedAudio(
    val source: AudioSource,
    /** The file's length, or where the ayah ends in it when the file holds more than the ayah. */
    val durationMs: Long?,
    val words: List<WordTiming>,
    /** True when [source] is the file of the whole surah, not of one ayah. */
    val coversWholeSurah: Boolean,
    /** Where the ayah begins in the file; 0 for a file that holds just the ayah. */
    val windowStartMs: Long = 0L,
    /** Where the ayah ends in the file; null when it plays to the end of the file. */
    val windowEndMs: Long? = null,
)

fun interface AudioResolver {

    /** Returns the audio to play for [ayah], or null when the reciter or the file is unknown. */
    suspend fun resolve(reciterId: String, ayah: AyahRef): ResolvedAudio?

    /**
     * Returns the reciter's Basmalah to play before [before], the first ayah of a surah, or null
     * when the reciter has none to play on its own (a surah file already starts with it).
     */
    suspend fun resolveBasmalah(reciterId: String, before: AyahRef): ResolvedAudio? = null
}

/**
 * Chooses the file for an ayah. The URL in the timing pack comes first because the word timings
 * were made against that exact file. Without it, the reciter's own ayah pattern is used, and for a
 * reciter with no ayah files the surah file.
 */
class ReciterAudioResolver(
    private val reciters: ReciterRepository,
    private val store: LocalAudioStore,
    private val quranText: QuranTextRepository? = null,
    /** Runs the disk check for a downloaded clip; tests pass their own scheduler's dispatcher. */
    private val diskDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioResolver {

    private var chapterNames: Map<Int, String>? = null

    override suspend fun resolve(reciterId: String, ayah: AyahRef): ResolvedAudio? {
        val reciter = reciters.reciters().firstOrNull { it.id == reciterId } ?: return null
        val timedAudio = reciters.ayahAudio(reciterId, ayah)
        val ayahUrlPattern = reciter.ayahUrlPattern
        val ayahKey = store.ayahKey(reciterId, ayah)
        val title = titleOf(ayah)
        return when {
            timedAudio != null && timedAudio.isInWholeSurahFile ->
                resolveInSurahFile(reciter, timedAudio, ayah, title)
            timedAudio != null ->
                ResolvedAudio(
                    source = source(reciter, timedAudio.url, ayahKey, title),
                    durationMs = timedAudio.durationMs?.toLong(),
                    words = timedAudio.words,
                    coversWholeSurah = false,
                )
            ayahUrlPattern != null ->
                ResolvedAudio(
                    source =
                        source(reciter, AudioUrls.ayahUrl(ayahUrlPattern, ayah), ayahKey, title),
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
                            title,
                        ),
                    durationMs = null,
                    words = emptyList(),
                    coversWholeSurah = true,
                )
        }
    }

    @Suppress("ReturnCount") // Guard clauses: no reciter audio, or a surah file.
    override suspend fun resolveBasmalah(reciterId: String, before: AyahRef): ResolvedAudio? {
        val fatihahOpening = AyahRef(1, 1)
        val opening = resolve(reciterId, fatihahOpening) ?: return null
        if (opening.coversWholeSurah) return null
        // Same audio as ayah 1:1, but titled with the ayah it introduces.
        return opening.copy(source = opening.source.copy(title = titleOf(before)))
    }

    /**
     * An ayah that lies inside a surah file plays from its clip when that is on disk, else from the
     * surah file at the ayah's own position.
     */
    private suspend fun resolveInSurahFile(
        reciter: Reciter,
        timedAudio: AyahAudio,
        ayah: AyahRef,
        title: String,
    ): ResolvedAudio {
        val clipKey = store.ayahKey(reciter.id, ayah)
        val clipOnDisk = withContext(diskDispatcher) { store.fileForKey(clipKey)?.isFile == true }
        return windowedResolvedAudio(
            timed = timedAudio,
            clipSource = source(reciter, timedAudio.url, clipKey, title),
            surahSource =
                source(reciter, timedAudio.url, store.surahKey(reciter.id, ayah.surah), title),
            clipOnDisk = clipOnDisk,
        )
    }

    private fun source(reciter: Reciter, url: String, key: String, title: String) =
        AudioSource(url = url, localKey = key, title = title, artist = reciter.name)

    /** "Al-Baqarah 2:6" when chapter names are available, else "2:6". */
    private suspend fun titleOf(ayah: AyahRef): String {
        val names =
            chapterNames
                ?: quranText
                    ?.chapters()
                    ?.associate { it.number to it.nameSimple }
                    ?.also { chapterNames = it }
        val name = names?.get(ayah.surah) ?: return ayah.toString()
        return "$name $ayah"
    }
}
