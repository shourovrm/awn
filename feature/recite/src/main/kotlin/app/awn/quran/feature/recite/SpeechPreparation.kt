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
 * low-priority thread of its own, so the first "recite from here" does not wait for them. On the
 * phone these steps took about 45 s of the first start and about 5 s of every later one.
 *
 * [build] does the slow work; it runs once on the background [executor] and its result is kept
 * until [release]. [prepare] may be called as often as needed.
 */
class SpeechPreparation(
    private val executor: Executor = backgroundExecutor,
    private val build: () -> PreparedSpeech,
) {

    private val lock = Any()
    private val mutableState = MutableStateFlow(SpeechState.Idle)
    private var pending = CompletableFuture<PreparedSpeech>()

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
        synchronized(lock) {
            val current = mutableState.value
            if (current == SpeechState.Preparing || current == SpeechState.Ready) return
            if (current == SpeechState.Failed) pending = CompletableFuture()
            mutableState.value = SpeechState.Preparing
            future = pending
        }
        executor.execute { buildInto(future) }
    }

    // Any failure is handed to whoever waits on the future and shown as the Failed state.
    @Suppress("TooGenericExceptionCaught")
    private fun buildInto(future: CompletableFuture<PreparedSpeech>) {
        try {
            future.complete(build())
            mutableState.value = SpeechState.Ready
        } catch (problem: Exception) {
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

    /** Frees the loaded model; the next [prepare] loads it again. */
    fun release() {
        synchronized(lock) {
            if (mutableState.value == SpeechState.Ready) pending.getNow(null)?.close()
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
