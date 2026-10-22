package app.awn.quran.feature.recite

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeProgressEvents
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.player.fakes.FakeAudioPlayer
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.TrackerFactory
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordKeyLike
import app.awn.quran.tracker.WordState
import app.awn.quran.tracker.fakes.FakeReferencePhonemes
import app.awn.quran.tracker.fakes.FakeTracker
import app.awn.quran.tracker.fakes.FakeTrackerFactory
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout

/**
 * A [ReciteController] on fake collaborators. Surah 1 has ayah 1 (two words) and ayah 2 (one word)
 * on page 1, ayah 3 (one word) on page 2 and surah 2 ayah 1 (one word) on page 3.
 */
// Each argument swaps one fake for a test that needs another behaviour.
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerFixture(
    storeFolder: File,
    inputBlocks: Int = 1,
    prepareImmediately: Boolean = true,
    phonemeSource: () -> PhonemeSource = { OnePhonemePerBlockSource() },
    private val fakeTrackers: FakeTrackerFactory? = FakeTrackerFactory(),
    reference: ReferencePhonemes? = null,
    trackerFactory: TrackerFactory? = null,
    val dueAyahs: MutableList<AyahRef> = mutableListOf(),
    /** Replaces the scripted input, for a test that needs the microphone to stay open. */
    microphone: (() -> TrackerInput)? = null,
    /** Replaces the test dispatcher, for an input that blocks its thread. */
    recognitionThread: CoroutineDispatcher? = null,
    /** Whether the chosen reciter has word timings, so Listen can play a single word. */
    hasWordTimings: Boolean = false,
    /** The Word audio reciter of Settings; null means the recite reciter is used. */
    wordReciter: String? = null,
) {
    val first = WordKey(1, 1, 1)
    val second = WordKey(1, 1, 2)
    val third = WordKey(1, 2, 1)
    val fourth = WordKey(1, 3, 1)
    val fifth = WordKey(2, 1, 1)

    val graded = mutableListOf<Pair<AyahRef, AyahGrade>>()
    val opened = mutableListOf<AyahRef>()

    val audioPlayer = FakeAudioPlayer()
    val progress = FakeProgressEvents()

    val tracker: FakeTracker
        get() = checkNotNull(fakeTrackers).created.last()

    private val settingsScope = CoroutineScope(Dispatchers.IO)

    val settings =
        ReciteSettings(
            PreferenceDataStoreFactory.create(scope = settingsScope) {
                File(storeFolder, "controller.preferences_pb")
            },
        )

    // Preparation work held back until finishPreparing() when prepareImmediately is false.
    private val pendingWork = ArrayDeque<Runnable>()

    /** How many times a loaded speech model was closed. */
    var closedModelCount = 0
        private set

    /** The grace period timer: nothing is released after a close until a test fires it. */
    val graceTimer = ManualDelayedRunner()

    /** How many times a speech model was built. */
    var builtModelCount = 0
        private set

    val speech =
        SpeechPreparation(
            executor = { work -> if (prepareImmediately) work.run() else pendingWork += work },
            delayedRunner = graceTimer,
        ) {
            builtModelCount++
            object : PreparedSpeech {
                override val referencePhonemes: ReferencePhonemes = reference ?: referenceWords()

                override fun newPhonemeSource(): PhonemeSource = phonemeSource()

                override fun close() {
                    closedModelCount++
                }
            }
        }

    val controller: ReciteController

    // Everything the fixture started that outlives a test body: a blocked recogniser thread, a load
    // that waits for preparation, and the controller's coroutines.
    private val controllerJob = Job()
    private val openedInputs = mutableListOf<TrackerInput>()
    private val ownedRecognitionThread = recognitionThread

    init {
        synchronized(created) { created += this }
        val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        val text =
            FakeQuranTextRepository(
                wordsByAyah =
                    mutableMapOf(
                        AyahRef(1, 1) to listOf(word(first, "بِسْمِ"), word(second, "ٱللَّهِ")),
                        AyahRef(1, 2) to listOf(word(third, "ٱلْحَمْدُ")),
                        AyahRef(1, 3) to listOf(word(fourth, "ٱلرَّحْمَٰنِ")),
                        AyahRef(2, 1) to listOf(word(fifth, "الم")),
                    ),
                pageByAyah =
                    mutableMapOf(
                        AyahRef(1, 1) to 1,
                        AyahRef(1, 2) to 1,
                        AyahRef(1, 3) to 2,
                        AyahRef(2, 1) to 3,
                    ),
            )
        controller =
            ReciteController(
                quranText = text,
                progressEvents = progress,
                settings = settings,
                audioPlayer = audioPlayer,
                speech = speech,
                inputFactory = {
                    val input = microphone?.invoke() ?: ScriptedInput(inputBlocks)
                    synchronized(openedInputs) { openedInputs += input }
                    input
                },
                scope = CoroutineScope(dispatcher + controllerJob),
                trackerFactory = trackerFactory ?: fakeTrackers ?: DefaultTrackerFactory,
                recognitionDispatcher = recognitionThread ?: dispatcher,
                gradeSink = { ayah, grade -> graded += ayah to grade },
                revisionQueue = { dueAyahs.toList() },
                onOpenAyah = { opened += it },
                hasWordTimings = { _, _ -> hasWordTimings },
                wordReciterId = { reciteReciter -> wordReciter ?: reciteReciter },
            )
    }

    /**
     * Ends what the test left running. A load that still waits for preparation blocks one
     * Dispatchers.Default thread for good, and CI has only two of them, so later tests would hang.
     */
    fun close() {
        synchronized(openedInputs) { openedInputs.forEach { (it as? AutoCloseable)?.close() } }
        finishPreparing()
        // Joined so nothing from this test throws after it ends and fails the next test.
        runBlocking {
            withTimeout(JOIN_TIMEOUT_MILLIS) {
                controllerJob.cancelAndJoin()
                settingsScope.coroutineContext.job.cancelAndJoin()
            }
        }
        (ownedRecognitionThread as? AutoCloseable)?.close()
    }

    /** Runs the preparation work that was held back. */
    fun finishPreparing() {
        while (pendingWork.isNotEmpty()) pendingWork.removeFirst().run()
    }

    /** Queues what the tracker reports for the next block. */
    fun script(vararg events: TrackerEvent) {
        tracker.scriptedBatches.addLast(events.toList())
    }

    fun confirmed(index: Int) = TrackerEvent.WordChanged(index, WordState.Confirmed)

    private fun word(key: WordKey, text: String) = Word(key, text, null, null)

    private fun referenceWords(): ReferencePhonemes =
        FakeReferencePhonemes(
            mutableMapOf(
                (1 to 1) to listOf(reference(first), reference(second)),
                (1 to 2) to listOf(reference(third)),
                (1 to 3) to listOf(reference(fourth)),
                (2 to 1) to listOf(reference(fifth)),
            ),
        )

    private fun reference(key: WordKey) =
        ReferenceWord(WordKeyLike(key.surah, key.ayah, key.wordPosition), listOf("a"))

    companion object {
        private const val JOIN_TIMEOUT_MILLIS = 10_000L
        private val created = mutableListOf<ControllerFixture>()

        /** Closes every fixture made since the last call. */
        fun closeAll() {
            val toClose = synchronized(created) { created.toList().also { created.clear() } }
            toClose.forEach { it.close() }
        }
    }
}

/** Closes every [ControllerFixture] a test made, even when the test fails. */
class ControllerFixtures : org.junit.rules.ExternalResource() {
    override fun after() = ControllerFixture.closeAll()
}
