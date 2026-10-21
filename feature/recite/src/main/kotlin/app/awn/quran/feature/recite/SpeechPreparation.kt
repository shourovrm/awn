package app.awn.quran.feature.recite

import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the speech model and the reference text need before the first word can be followed. */
interface PreparedSpeech : AutoCloseable {
    val referencePhonemes: ReferencePhonemes

    /** Opens the recogniser for one recitation. */
    fun newPhonemeSource(): PhonemeSource
}

/** Runs something once after a delay, for the grace period before the model is released. */
fun interface DelayedRunner {

    /** Runs [action] after [delayMillis]; the returned function cancels it if it has not run. */
    fun runAfter(delayMillis: Long, action: Runnable): () -> Unit
}

enum class SpeechState {
    /** [SpeechPreparation.prepare] was not called yet. */
    Idle,
    Preparing,
    Ready,

    /** Copying, parsing or loading failed; [SpeechPreparation.prepare] tries again. */
    Failed,
}

/**
 * Gets the speech files, the 12.8 MB reference text and the speech model ready on a low-priority
 * thread of its own (the app loads the model in its `:speech` process). The loaded model holds
 * about 144 MB of native memory, so it is prepared when recite mode starts and [release]d after it
 * ends. On the phone this took 2.9 s from the tap (measured 2026-10-21).
 *
 * [build] does the slow work; it runs on the background [executor] and its result is kept until
 * [release]. [prepare] may be called as often as needed.
 */
class SpeechPreparation(
    private val executor: Executor = backgroundExecutor,
    private val delayedRunner: DelayedRunner = timerRunner,
    private val build: () -> PreparedSpeech,
) {

    private val lock = Any()
    private val mutableState = MutableStateFlow(SpeechState.Idle)
    private var pending = CompletableFuture<PreparedSpeech>()

    // Cancels the release that [releaseAfterGrace] scheduled; null when none is waiting. The token
    // names that release, so a timer that is already running when it is cancelled can tell.
    private var cancelGraceRelease: (() -> Unit)? = null
    private var graceToken: Any? = null

    // Counts releases. A build remembers the number it started under; when [release] has run since,
    // its result belongs to nobody and must be closed instead of published.
    private var generation = 0

    /** For the recite bar: show "getting ready" until this is [SpeechState.Ready]. */
    val state: StateFlow<SpeechState> = mutableState.asStateFlow()

    /** The reference text for the tracker; reading it waits for [prepare] to finish. */
    val referencePhonemes: ReferencePhonemes =
        object : ReferencePhonemes {
            override fun forAyah(surah: Int, ayah: Int): List<ReferenceWord> =
                awaitReady().referencePhonemes.forAyah(surah, ayah)
        }

    /** Starts the work in the background unless it is running or done. Returns at once. */
    fun prepare() {
        val future: CompletableFuture<PreparedSpeech>
        val startedGeneration: Int
        synchronized(lock) {
            cancelPendingGraceRelease()
            val current = mutableState.value
            if (current == SpeechState.Preparing || current == SpeechState.Ready) return
            if (current == SpeechState.Failed) pending = CompletableFuture()
            mutableState.value = SpeechState.Preparing
            future = pending
            startedGeneration = generation
        }
        executor.execute { buildInto(future, startedGeneration) }
    }

    // Any failure is handed to whoever waits on the future and shown as the Failed state.
    @Suppress("TooGenericExceptionCaught")
    private fun buildInto(future: CompletableFuture<PreparedSpeech>, startedGeneration: Int) {
        val prepared =
            try {
                build()
            } catch (problem: Exception) {
                publishFailure(future, startedGeneration, problem)
                return
            }
        val published = publishSuccess(future, startedGeneration, prepared)
        if (!published) prepared.close()
    }

    private fun publishSuccess(
        future: CompletableFuture<PreparedSpeech>,
        startedGeneration: Int,
        prepared: PreparedSpeech,
    ): Boolean =
        synchronized(lock) {
            if (generation != startedGeneration) return@synchronized false
            future.complete(prepared)
            mutableState.value = SpeechState.Ready
            true
        }

    private fun publishFailure(
        future: CompletableFuture<PreparedSpeech>,
        startedGeneration: Int,
        problem: Exception,
    ) {
        synchronized(lock) {
            // A released build has nothing to report: its future was already ended by [release].
            if (generation != startedGeneration) return
            future.completeExceptionally(problem)
            mutableState.value = SpeechState.Failed
        }
    }

    /**
     * Returns the prepared speech, starting the work if needed and blocking until it is done. Call
     * it from a background thread. Throws [IllegalStateException] when preparing failed.
     */
    fun awaitReady(): PreparedSpeech {
        prepare()
        val future = synchronized(lock) { pending }
        try {
            return future.get()
        } catch (problem: ExecutionException) {
            throw IllegalStateException("The speech files could not be prepared.", problem)
        }
    }

    /** Opens a recogniser for one recitation; blocks like [awaitReady]. */
    fun newPhonemeSource(): PhonemeSource = awaitReady().newPhonemeSource()

    /**
     * Frees the loaded model; the next [prepare] loads it again. A build that is still running
     * closes its own result when it ends, and anyone waiting on it gets an [IllegalStateException]
     * instead of waiting for a model nobody will use.
     */
    fun release() {
        synchronized(lock) {
            cancelPendingGraceRelease()
            generation += 1
            // A failed future throws on getNow, so only a finished build is closed here.
            if (mutableState.value == SpeechState.Ready) pending.getNow(null)?.close()
            pending.completeExceptionally(IllegalStateException("The speech model was released."))
            pending = CompletableFuture()
            mutableState.value = SpeechState.Idle
        }
    }

    /**
     * Keeps the model for [graceMillis] and then releases it, so that reciting again soon after
     * closing starts at once. [prepare] inside that time cancels the release, and [release] does it
     * now. A later call replaces an earlier one.
     */
    fun releaseAfterGrace(graceMillis: Long = DEFAULT_GRACE_MILLIS) {
        synchronized(lock) {
            cancelPendingGraceRelease()
            if (mutableState.value == SpeechState.Idle) return
            val token = Any()
            graceToken = token
            cancelGraceRelease =
                delayedRunner.runAfter(graceMillis) {
                    // A restart cancels the timer, but a timer already running can still arrive
                    // here: only the release that is still waiting may end the model.
                    if (synchronized(lock) { graceToken === token }) release()
                }
        }
    }

    // Must be called with [lock] held.
    private fun cancelPendingGraceRelease() {
        cancelGraceRelease?.invoke()
        cancelGraceRelease = null
        graceToken = null
    }

    companion object {
        /** How long the model stays loaded after the recite bar closes. */
        const val DEFAULT_GRACE_MILLIS = 120_000L

        private val timerRunner = DelayedRunner { delayMillis, action ->
            val future = graceTimer.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            return@DelayedRunner { future.cancel(false) }
        }

        private val graceTimer: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "awn-speech-grace").apply { isDaemon = true }
            }

        // One thread at the lowest priority: the copy and parse must not slow the first screen.
        private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "awn-speech-prepare").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }
}
