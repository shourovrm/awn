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

        /** Plays of the current ayah finished so far. */
        var completedPlays = 0

        /** Times the whole range has finished so far. */
        var completedRounds = 0

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

    private val mutableOptions = MutableStateFlow(PlaybackOptions())
    val options: StateFlow<PlaybackOptions> = mutableOptions

    private var session: Session? = null
    private var currentFile: CurrentFile? = null
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

    /** Applies [newOptions] at once: the speed to the engine, the repeats at the next boundary. */
    fun setOptions(newOptions: PlaybackOptions) {
        mutableOptions.value = newOptions.coerced()
        engine.setSpeed(mutableOptions.value.speed)
    }

    /** Skips to the next ayah; after the last one the range ends or repeats as usual. */
    fun next() {
        val active = rangeSession() ?: return
        restartSession(active) { advance(active) }
    }

    /** Restarts the ayah when it has played past [RESTART_AFTER_MS], else plays the one before. */
    fun previous() {
        val active = rangeSession() ?: return
        val playedMs = engine.positionMs()
        restartSession(active) {
            if (playedMs <= RESTART_AFTER_MS) stepBack(active)
            startCurrentAyah()
        }
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
        engine.setSpeed(mutableOptions.value.speed)
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

    /** The running session when it plays a range of ayahs, not a single word. */
    private fun rangeSession(): Session? = session?.takeIf { it.singleWordPosition == null }

    /** Drops what is loading now and runs [block] for [active] on the controller's thread. */
    private fun restartSession(active: Session, block: suspend () -> Unit) {
        generation++
        active.completedPlays = 0
        scope.launch { runGuarded { block() } }
    }

    /** Moves to the ayah before the current one; a surah file is one step back by surah. */
    private fun stepBack(active: Session) {
        if (active.index == 0) return
        val playingSurahFile = currentFile?.audio?.coversWholeSurah == true
        active.index--
        if (playingSurahFile) {
            val surah = active.ayahs[active.index].surah
            active.index = active.ayahs.indexOfFirst { it.surah == surah }
        }
    }

    private suspend fun finishOnePlay() {
        val active = session ?: return
        active.completedPlays++
        if (!isFinished(active.completedPlays, mutableOptions.value.repeatEachAyah)) {
            startCurrentAyah()
            return
        }
        active.completedPlays = 0
        advance(active)
    }

    /** True once [completed] plays reach [wanted]; an infinite repeat never finishes. */
    private fun isFinished(completed: Int, wanted: Int): Boolean =
        wanted != PlaybackOptions.INFINITE_REPEAT && completed >= wanted

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
        if (active.index < active.ayahs.size) {
            startCurrentAyah()
            return
        }
        active.completedRounds++
        val roundsWanted =
            if (active.singleWordPosition == null) mutableOptions.value.repeatRange else 1
        if (isFinished(active.completedRounds, roundsWanted)) {
            stop()
        } else {
            active.index = 0
            startCurrentAyah()
        }
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

    private companion object {
        /** Past this much playing time "previous" restarts the ayah instead of leaving it. */
        const val RESTART_AFTER_MS = 3_000L
    }
}
