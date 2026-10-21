package app.awn.quran.feature.recite

import app.awn.quran.tracker.JsonReferencePhonemes
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.SherpaRecognizer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the speech files are on disk once copied out of the APK. */
data class SpeechFiles(val model: File, val tokens: File, val referencePhonemes: File)

/** What the speech model and the reference text need before the first word can be followed. */
interface PreparedSpeech : AutoCloseable {
    val referencePhonemes: ReferencePhonemes

    /** Opens the recogniser for one recitation. */
    fun newPhonemeSource(): PhonemeSource
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
 * Copies the speech files, parses the 12.8 MB reference file and loads the speech model on a
 * low-priority thread of its own. The loaded model holds about 144 MB of native memory, so it is
 * prepared when recite mode starts and [release]d when it ends. On the phone these steps took about
 * 45 s the first time and about 5 s every later time.
 *
 * [build] does the slow work; it runs on the background [executor] and its result is kept until
 * [release]. [prepare] may be called as often as needed.
 */
class SpeechPreparation(
    private val executor: Executor = backgroundExecutor,
    private val build: () -> PreparedSpeech,
) {

    private val lock = Any()
    private val mutableState = MutableStateFlow(SpeechState.Idle)
    private var pending = CompletableFuture<PreparedSpeech>()

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
            generation += 1
            // A failed future throws on getNow, so only a finished build is closed here.
            if (mutableState.value == SpeechState.Ready) pending.getNow(null)?.close()
            pending.completeExceptionally(IllegalStateException("The speech model was released."))
            pending = CompletableFuture()
            mutableState.value = SpeechState.Idle
        }
    }

    companion object {
        // One thread at the lowest priority: the copy and parse must not slow the first screen.
        private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "awn-speech-prepare").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }

        /**
         * The real thing: [installFiles] copies the files out of the APK (blocking, throws
         * [IllegalStateException] when it cannot), then the reference is parsed and the speech
         * model loaded.
         */
        fun fromFiles(installFiles: () -> SpeechFiles): SpeechPreparation = SpeechPreparation {
            val files = installFiles()
            val reference = JsonReferencePhonemes.fromFile(files.referencePhonemes)
            val recognizer =
                try {
                    SherpaRecognizer(files.model.path, files.tokens.path)
                } catch (problem: UnsatisfiedLinkError) {
                    // The session reports IllegalStateException as "can't use the microphone".
                    throw IllegalStateException("The speech library could not be loaded.", problem)
                }
            object : PreparedSpeech {
                override val referencePhonemes = reference

                override fun newPhonemeSource() = recognizer.newSource()

                override fun close() = recognizer.close()
            }
        }
    }
}
