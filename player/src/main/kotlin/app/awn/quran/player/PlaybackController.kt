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
 * date. It owns the rules of playback: which turn comes next (see [PlaySchedule]), how long the
 * learner gets to repeat, where a single word starts and stops, the sleep timer, and when to retry
 * on the fallback domain.
 *
 * While a file plays the controller also tells the engine which file follows ([preloadNext]), so
 * the next ayah starts without a gap.
 *
 * Every method must be called on the thread of [scope] (the main thread in the app). [clockMs] is a
 * monotonic millisecond clock; tests pass the virtual time of their scheduler.
 */
@Suppress("TooManyFunctions") // The engine listener callbacks and the public controls are one unit.
class PlaybackController(
    private val engine: PlaybackEngine,
    private val resolver: AudioResolver,
    private val playlistBuilder: PlaylistBuilder,
    private val scope: CoroutineScope,
    private val tickMs: Long = WordTimeline.RESOLUTION_MS,
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : PlaybackEngine.Listener {

    private class Session(
        val reciterId: String,
        val schedule: PlaySchedule,
        /** The word to play alone, or null to play every ayah in full. */
        val singleWordPosition: Int?,
    ) {
        lateinit var position: PlaySchedule.Position

        /** True once the first file of the session has started. */
        var started = false
    }

    private class PlayWindow(val startMs: Long, val stopAtMs: Long?)

    private class CurrentFile(
        val audio: ResolvedAudio,
        val timeline: WordTimeline,
        val window: PlayWindow,
        val turn: Turn,
    ) {
        var fallbackTried = false
    }

    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = mutableState

    private val mutableOptions = MutableStateFlow(PlaybackOptions())
    val options: StateFlow<PlaybackOptions> = mutableOptions

    private val mutableSleepTimer = MutableStateFlow<SleepTimer?>(null)
    val sleepTimer: StateFlow<SleepTimer?> = mutableSleepTimer

    private var session: Session? = null
    private var currentFile: CurrentFile? = null
    private var generation = 0
    private var tickerJob: Job? = null
    private var turnJob: Job? = null
    private var preloadJob: Job? = null
    private var sleepJob: Job? = null

    /** The end of the silence that waits for the learner, or null when not in one. */
    private var silenceEndsAtMs: Long? = null

    /** Silence left when playback was paused during it, to be waited out on resume. */
    private var pausedSilenceMs: Long? = null

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
        val schedule = PlaySchedule(ayahs, cumulative = mutableOptions.value.cumulative)
        beginSession(Session(reciterId, schedule, singleWordPosition = null))
        startCurrentTurn()
    }

    suspend fun playWord(reciterId: String, ayah: AyahRef, position: Int) {
        val schedule = PlaySchedule(listOf(ayah), cumulative = false, withBasmalah = false)
        beginSession(Session(reciterId, schedule, singleWordPosition = position))
        startCurrentTurn()
    }

    fun pause() {
        val active = session ?: return
        if (mutableState.value !is PlaybackState.Playing) return
        tickerJob?.cancel()
        freezeSilence()
        engine.pause()
        mutableState.value =
            PlaybackState.Paused(
                active.reciterId,
                currentAyah(active),
                engine.positionMs(),
                currentFile?.turn?.step,
            )
    }

    fun resume() {
        if (session == null || mutableState.value !is PlaybackState.Paused) return
        continueAfterPause()
    }

    fun stop() {
        generation++
        releaseSession()
        mutableSleepTimer.value = null
        sleepJob?.cancel()
        mutableState.value = PlaybackState.Idle
    }

    /**
     * Applies [newOptions] at once: the speed to the engine, the repeats and the pause at the next
     * boundary. Nothing restarts. Cumulative playback is read only when a range starts.
     */
    fun setOptions(newOptions: PlaybackOptions) {
        val before = mutableOptions.value
        mutableOptions.value = newOptions.coerced()
        engine.setSpeed(mutableOptions.value.speed)
        val after = mutableOptions.value
        val changesWhatFollows =
            before.repeatEachAyah != after.repeatEachAyah ||
                before.repeatRange != after.repeatRange ||
                before.pauseAfterAyah != after.pauseAfterAyah
        if (changesWhatFollows) preloadNext()
    }

    /** Loads what the first play needs (reciter list, surah names) ahead of the user's tap. */
    fun warmUp(reciterId: String) {
        scope.launch { runGuarded { resolver.resolve(reciterId, AyahRef(1, 1)) } }
    }

    /** Starts [timer], replacing the running one; null cancels it. */
    fun setSleepTimer(timer: SleepTimer?) {
        sleepJob?.cancel()
        mutableSleepTimer.value = timer
        if (timer is SleepTimer.Minutes) {
            sleepJob = scope.launch {
                delay(timer.minutes * MILLIS_PER_MINUTE)
                stop()
            }
        }
        preloadNext()
    }

    /** Skips to the next ayah (or cumulative step); after the last one the range ends as usual. */
    fun next() {
        val active = rangeSession() ?: return
        restartSession {
            val following =
                active.schedule.afterUnit(active.position, effectiveOptions(active), wholeSurah())
            moveTo(active, following)
        }
    }

    /** Restarts the ayah when it has played past [RESTART_AFTER_MS], else plays the one before. */
    fun previous() {
        val active = rangeSession() ?: return
        val playedMs = engine.positionMs()
        restartSession {
            active.position =
                if (playedMs <= RESTART_AFTER_MS) {
                    active.schedule.beforeUnit(active.position, wholeSurah())
                } else {
                    active.position.copy(completedPlays = 0)
                }
            startCurrentTurn()
        }
    }

    override fun onEnded() {
        turnJob = scope.launch { runGuarded { finishOnePlay() } }
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
            freezeSilence()
            mutableState.value =
                PlaybackState.Paused(
                    active.reciterId,
                    currentAyah(active),
                    engine.positionMs(),
                    currentFile?.turn?.step,
                )
        } else if (playWhenReady && current is PlaybackState.Paused) {
            continueAfterPause(resumeEngine = false)
        }
    }

    private fun beginSession(newSession: Session) {
        generation++
        releaseSession()
        newSession.position = newSession.schedule.start()
        session = newSession
    }

    private fun releaseSession() {
        tickerJob?.cancel()
        turnJob?.cancel()
        preloadJob?.cancel()
        engine.stop()
        session = null
        currentFile = null
        silenceEndsAtMs = null
        pausedSilenceMs = null
    }

    private fun currentAyah(active: Session): AyahRef = active.schedule.ayahAt(active.position)

    /** The options the schedule follows; a single word repeats itself but never its "range". */
    private fun effectiveOptions(active: Session): PlaybackOptions =
        if (active.singleWordPosition == null) {
            mutableOptions.value
        } else {
            mutableOptions.value.copy(repeatRange = 1)
        }

    private fun wholeSurah(): Boolean = currentFile?.audio?.coversWholeSurah == true

    /** Resolves and starts the turn at the session's position; skips a Basmalah with no audio. */
    @Suppress("ReturnCount") // Each return drops a request that a newer one replaced.
    private suspend fun startCurrentTurn() {
        val active = session ?: return
        val startedGeneration = generation
        var turn = active.schedule.turnAt(active.position)
        var audio = resolveTurn(active, turn)
        if (startedGeneration != generation) return
        if (audio == null && turn.kind == TurnKind.Basmalah) {
            // The reciter has no separate Basmalah (a surah file starts with it): go on to the
            // ayah.
            active.position = active.position.copy(inBasmalah = false)
            turn = active.schedule.turnAt(active.position)
            audio = resolveTurn(active, turn)
            if (startedGeneration != generation) return
        }
        val problem = startFile(active, turn, audio)
        if (problem != null) fail(problem)
    }

    private suspend fun resolveTurn(active: Session, turn: Turn): ResolvedAudio? =
        when (turn.kind) {
            TurnKind.Basmalah -> resolver.resolveBasmalah(active.reciterId, turn.ayah)
            TurnKind.Ayah -> resolver.resolve(active.reciterId, turn.ayah)
        }

    /** Starts the engine on [audio]; returns what went wrong, or null when playback started. */
    private fun startFile(active: Session, turn: Turn, audio: ResolvedAudio?): String? {
        return if (audio == null) "Audio not found for ${turn.ayah}"
        else startAudio(active, turn, audio)
    }

    private fun startAudio(active: Session, turn: Turn, audio: ResolvedAudio): String? {
        if (!active.started && audio.coversWholeSurah) {
            // A surah file cannot be cut into the steps of a cumulative drill.
            active.schedule.dropCumulative()
        }
        active.started = true
        val timeline = WordTimeline(audio.words)
        val window =
            windowFor(active, audio, timeline)
                ?: return "No word timing for ${active.singleWordPosition} of ${turn.ayah}"
        currentFile = CurrentFile(audio, timeline, window, turn)
        silenceEndsAtMs = null
        engine.setSpeed(mutableOptions.value.speed)
        engine.start(audio.source, window.startMs, window.stopAtMs)
        publishPlaying()
        startTicker()
        preloadNext()
        return null
    }

    /** Returns where to start and stop in the file, or null if the word has no usable timing. */
    private fun windowFor(
        active: Session,
        audio: ResolvedAudio,
        timeline: WordTimeline,
    ): PlayWindow? {
        val wordPosition =
            active.singleWordPosition ?: return PlayWindow(audio.windowStartMs, audio.windowEndMs)
        return timeline.timingOf(wordPosition)?.let { wordWindow(it, audio.durationMs) }
    }

    private fun wordWindow(timing: WordTiming, fileDurationMs: Long?): PlayWindow? {
        val startMs = timing.startMs.toLong().coerceAtLeast(0L)
        val endMs = minOf(timing.endMs.toLong(), fileDurationMs ?: Long.MAX_VALUE)
        return if (endMs > startMs) PlayWindow(startMs, endMs) else null
    }

    /** The running session when it plays a range of ayahs, not a single word. */
    private fun rangeSession(): Session? = session?.takeIf { it.singleWordPosition == null }

    /** Drops what is loading now and runs [block] on the controller's thread. */
    private fun restartSession(block: suspend () -> Unit) {
        generation++
        turnJob?.cancel()
        silenceEndsAtMs = null
        pausedSilenceMs = null
        session?.let { it.position = it.position.copy(completedPlays = 0) }
        turnJob = scope.launch { runGuarded { block() } }
    }

    private suspend fun moveTo(active: Session, position: PlaySchedule.Position?) {
        if (position == null) {
            stop()
            return
        }
        active.position = position
        startCurrentTurn()
    }

    /** Runs when the engine has finished a file: picks the next turn, waits for the learner. */
    private suspend fun finishOnePlay() {
        val active = session ?: return
        val finishedTurn = active.schedule.turnAt(active.position)
        val following =
            active.schedule.afterPlay(active.position, effectiveOptions(active), wholeSurah())
        if (following == null || sleepTimerEndsAfter(finishedTurn, active, following)) {
            stop()
            return
        }
        val silenceMs = silenceAfter(finishedTurn, active)
        active.position = following
        if (silenceMs > 0L) waitForLearner(silenceMs)
        startCurrentTurn()
    }

    /**
     * True when the sleep timer wants playback to end between [finished] and what [following] is.
     */
    private fun sleepTimerEndsAfter(
        finished: Turn,
        active: Session,
        following: PlaySchedule.Position,
    ): Boolean =
        when (mutableSleepTimer.value) {
            SleepTimer.EndOfAyah -> finished.kind == TurnKind.Ayah
            SleepTimer.EndOfSurah ->
                active.schedule.ayahAt(following).surah != finished.ayah.surah ||
                    following.round != active.position.round
            is SleepTimer.Minutes,
            null -> false
        }

    /** How long the learner gets after [finished]: a share of the time the ayah took to play. */
    @Suppress("ReturnCount") // Guard clauses: no pause wanted, nothing to wait after, no file.
    private fun silenceAfter(finished: Turn, active: Session): Long {
        val factor = mutableOptions.value.pauseAfterAyah.lengthFactor
        if (factor == 0.0 || finished.kind != TurnKind.Ayah) return 0L
        if (active.singleWordPosition != null || wholeSurah()) return 0L
        val file = currentFile ?: return 0L
        val measuredFileMs = engine.durationMs() ?: engine.positionMs()
        val playedMs = file.audio.playedLengthMs(measuredFileMs) / mutableOptions.value.speed
        return (playedMs * factor).toLong()
    }

    /** Waits [lengthMs] of silence, shown as playing with no word lit. */
    private suspend fun waitForLearner(lengthMs: Long) {
        silenceEndsAtMs = clockMs() + lengthMs
        publishPlaying()
        delay(lengthMs)
        silenceEndsAtMs = null
    }

    /** Remembers what is left of the silence when the learner pauses during it. */
    private fun freezeSilence() {
        val endsAtMs = silenceEndsAtMs ?: return
        pausedSilenceMs = (endsAtMs - clockMs()).coerceAtLeast(0L)
        silenceEndsAtMs = null
        turnJob?.cancel()
    }

    private fun continueAfterPause(resumeEngine: Boolean = true) {
        val silenceLeftMs = pausedSilenceMs
        if (silenceLeftMs == null) {
            if (resumeEngine) engine.resume()
            publishPlaying()
            startTicker()
            return
        }
        pausedSilenceMs = null
        startTicker()
        turnJob = scope.launch {
            runGuarded {
                waitForLearner(silenceLeftMs)
                startCurrentTurn()
            }
        }
    }

    /**
     * Tells the engine which file follows the one playing, so the next ayah starts with no gap.
     * Nothing is prepared when the learner's pause, the sleep timer or the end of the range means
     * the engine must not move on by itself.
     */
    private fun preloadNext() {
        val active = session ?: return
        preloadJob?.cancel()
        preloadJob = scope.launch {
            runGuarded {
                val startedGeneration = generation
                val source = sourceAfterCurrent(active)
                if (startedGeneration == generation && session === active) {
                    engine.prepareNext(source)
                }
            }
        }
    }

    @Suppress("ReturnCount") // Guard clauses: each reason the engine must not move on by itself.
    private suspend fun sourceAfterCurrent(active: Session): AudioSource? {
        val file = currentFile ?: return null
        if (active.singleWordPosition != null) return null
        val options = effectiveOptions(active)
        if (file.turn.kind == TurnKind.Ayah && silenceAfter(file.turn, active) > 0L) return null
        val following =
            active.schedule.afterPlay(active.position, options, file.audio.coversWholeSurah)
                ?: return null
        if (sleepTimerEndsAfter(file.turn, active, following)) return null
        val followingTurn = active.schedule.turnAt(following)
        val sameFile = followingTurn.kind == file.turn.kind && followingTurn.ayah == file.turn.ayah
        // The engine can only carry on into a file that plays whole; a clip or a window in a surah
        // file starts at an offset, which needs a seek, so that file is loaded when its turn comes.
        if (sameFile) return file.audio.source.takeIf { file.audio.playsWholeFile }
        return resolveTurn(active, followingTurn)?.takeIf { it.playsWholeFile }?.source
    }

    private fun publishPlaying() {
        val active = session ?: return
        val file = currentFile ?: return
        val positionMs = engine.positionMs()
        val waiting = silenceEndsAtMs != null
        val lightsWord = file.turn.kind == TurnKind.Ayah && !waiting
        mutableState.value =
            PlaybackState.Playing(
                reciterId = active.reciterId,
                ayah = currentAyah(active),
                wordPosition = if (lightsWord) file.timeline.wordPositionAt(positionMs) else null,
                positionMs = positionMs,
                durationMs = file.audio.durationMs ?: engine.durationMs(),
                step = file.turn.step,
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
        mutableSleepTimer.value = null
        sleepJob?.cancel()
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
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
