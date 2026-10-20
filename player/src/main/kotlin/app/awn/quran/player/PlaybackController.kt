package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordTiming
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Plays a list of ayahs one file at a time through a [PlaybackEngine], and keeps [state] up to
 * date. It owns the rules of playback: which ayah comes next, how often an ayah repeats, where a
 * single word starts and stops, and when to retry on the fallback domain.
 *
 * Every method must be called on the thread of [scope] (the main thread in the app).
 */
@Suppress("TooManyFunctions") // The engine listener callbacks and the public controls are one unit.
class PlaybackController(
    private val engine: PlaybackEngine,
    private val resolver: AudioResolver,
    private val playlistBuilder: PlaylistBuilder,
    private val scope: CoroutineScope,
    private val tickMs: Long = WordTimeline.RESOLUTION_MS,
) : PlaybackEngine.Listener {

    private class Session(
        val reciterId: String,
        val ayahs: List<AyahRef>,
        /** The word to play alone, or null to play every ayah in full. */
        val singleWordPosition: Int?,
    ) {
        var index = 0
        var completedPlays = 0

        val currentAyah: AyahRef
            get() = ayahs[index]
    }

    private class PlayWindow(val startMs: Long, val stopAtMs: Long?)

    private class CurrentFile(
        val audio: ResolvedAudio,
        val timeline: WordTimeline,
        val window: PlayWindow,
    ) {
        var fallbackTried = false
    }

    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = mutableState

    private var session: Session? = null
    private var currentFile: CurrentFile? = null
    private var repeatTimes = 0
    private var generation = 0
    private var tickerJob: Job? = null

    init {
        engine.listener = this
    }

    suspend fun play(reciterId: String, from: AyahRef, to: AyahRef?) {
        val ayahs =
            try {
                playlistBuilder.build(from, to)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "Invalid ayah range")
                return
            }
        beginSession(Session(reciterId, ayahs, singleWordPosition = null))
        startCurrentAyah()
    }

    suspend fun playWord(reciterId: String, ayah: AyahRef, position: Int) {
        beginSession(Session(reciterId, listOf(ayah), singleWordPosition = position))
        startCurrentAyah()
    }

    fun pause() {
        val active = session ?: return
        if (mutableState.value !is PlaybackState.Playing) return
        tickerJob?.cancel()
        engine.pause()
        mutableState.value =
            PlaybackState.Paused(active.reciterId, active.currentAyah, engine.positionMs())
    }

    fun resume() {
        if (session == null || mutableState.value !is PlaybackState.Paused) return
        engine.resume()
        publishPlaying()
        startTicker()
    }

    fun stop() {
        generation++
        releaseSession()
        mutableState.value = PlaybackState.Idle
    }

    /** Each ayah (or the single word) plays [times] more times before the next one starts. */
    fun setRepeat(times: Int) {
        repeatTimes = times.coerceAtLeast(0)
    }

    override fun onEnded() {
        scope.launch { runGuarded { finishOnePlay() } }
    }

    override fun onFailed(reason: String) {
        val file = currentFile ?: return
        val fallbackUrl =
            if (file.fallbackTried) null else AudioUrls.fallbackUrl(file.audio.source.url)
        if (fallbackUrl == null) {
            fail("Playback failed: $reason")
            return
        }
        file.fallbackTried = true
        val resumeAtMs = maxOf(file.window.startMs, engine.positionMs())
        engine.start(file.audio.source.copy(url = fallbackUrl), resumeAtMs, file.window.stopAtMs)
    }

    override fun onPlayWhenReadyChangedExternally(playWhenReady: Boolean) {
        val active = session ?: return
        val current = mutableState.value
        if (!playWhenReady && current is PlaybackState.Playing) {
            tickerJob?.cancel()
            mutableState.value =
                PlaybackState.Paused(active.reciterId, active.currentAyah, engine.positionMs())
        } else if (playWhenReady && current is PlaybackState.Paused) {
            publishPlaying()
            startTicker()
        }
    }

    private fun beginSession(newSession: Session) {
        generation++
        releaseSession()
        session = newSession
    }

    private fun releaseSession() {
        tickerJob?.cancel()
        engine.stop()
        session = null
        currentFile = null
    }

    private suspend fun startCurrentAyah() {
        val active = session ?: return
        val startedGeneration = generation
        val ayah = active.currentAyah
        val audio = resolver.resolve(active.reciterId, ayah)
        if (startedGeneration != generation) return
        val problem = startFile(active, ayah, audio)
        if (problem != null) fail(problem)
    }

    /** Starts the engine on [audio]; returns what went wrong, or null when playback started. */
    private fun startFile(active: Session, ayah: AyahRef, audio: ResolvedAudio?): String? {
        return if (audio == null) "Audio not found for $ayah" else startAudio(active, ayah, audio)
    }

    private fun startAudio(active: Session, ayah: AyahRef, audio: ResolvedAudio): String? {
        val timeline = WordTimeline(audio.words)
        val window =
            windowFor(active, audio, timeline)
                ?: return "No word timing for ${active.singleWordPosition} of $ayah"
        currentFile = CurrentFile(audio, timeline, window)
        engine.start(audio.source, window.startMs, window.stopAtMs)
        publishPlaying()
        startTicker()
        return null
    }

    /** Returns where to start and stop in the file, or null if the word has no usable timing. */
    private fun windowFor(
        active: Session,
        audio: ResolvedAudio,
        timeline: WordTimeline,
    ): PlayWindow? {
        val wordPosition = active.singleWordPosition ?: return PlayWindow(0L, null)
        return timeline.timingOf(wordPosition)?.let { wordWindow(it, audio.durationMs) }
    }

    private fun wordWindow(timing: WordTiming, fileDurationMs: Long?): PlayWindow? {
        val startMs = timing.startMs.toLong().coerceAtLeast(0L)
        val endMs = minOf(timing.endMs.toLong(), fileDurationMs ?: Long.MAX_VALUE)
        return if (endMs > startMs) PlayWindow(startMs, endMs) else null
    }

    private suspend fun finishOnePlay() {
        val active = session ?: return
        active.completedPlays++
        val playsWanted = repeatTimes + 1
        if (active.completedPlays < playsWanted) {
            startCurrentAyah()
            return
        }
        active.completedPlays = 0
        advance(active)
    }

    private suspend fun advance(active: Session) {
        val finishedSurah = active.currentAyah.surah
        val playedWholeSurahFile = currentFile?.audio?.coversWholeSurah == true
        do {
            active.index++
        } while (
            playedWholeSurahFile &&
                active.index < active.ayahs.size &&
                active.ayahs[active.index].surah == finishedSurah
        )
        if (active.index >= active.ayahs.size) stop() else startCurrentAyah()
    }

    private fun publishPlaying() {
        val active = session ?: return
        val file = currentFile ?: return
        val positionMs = engine.positionMs()
        mutableState.value =
            PlaybackState.Playing(
                reciterId = active.reciterId,
                ayah = active.currentAyah,
                wordPosition = file.timeline.wordPositionAt(positionMs),
                positionMs = positionMs,
                durationMs = file.audio.durationMs ?: engine.durationMs(),
            )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(tickMs)
                publishPlaying()
            }
        }
    }

    private fun fail(message: String) {
        generation++
        releaseSession()
        mutableState.value = PlaybackState.Error(message)
    }

    /** Turns a failure inside an engine callback into the Error state instead of a crash. */
    private suspend fun runGuarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") error: RuntimeException) {
            fail("Playback failed: ${error.message}")
        }
    }
}
