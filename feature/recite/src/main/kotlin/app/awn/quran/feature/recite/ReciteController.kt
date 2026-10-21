package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.player.AudioPlayer
import app.awn.quran.tracker.AlignmentTracker
import app.awn.quran.tracker.Tracker
import app.awn.quran.tracker.TrackerConfig
import app.awn.quran.tracker.TrackerFactory
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The ayahs due for revision today; :app backs it with :feature:learn. */
fun interface RevisionQueue {

    /** Returns the ayahs due now, in any order. */
    suspend fun dueAyahs(): List<AyahRef>
}

/**
 * The one object :app creates for reciting on the Quran page. It owns the [RecitationSession], the
 * speech preparation and the settings, and offers the three things the Quran screen needs:
 * [reciteFrom] for `onReciteFrom`, [ReciteBar] for `reciteBar` and [rememberReciteWordStates] for
 * `reciteWordStates`.
 *
 * Create it once per process (for example in `AwnServices`) with a [scope] that outlives the
 * screens. The speech model (about 144 MB of native memory) is loaded when recite mode starts and
 * released after a grace period once [exit] has unloaded the session.
 */
// Each argument is a collaborator that :app supplies, and the entry points of recite mode (recite,
// revise, grade, exit, speed, hide words) are the controller's whole purpose.
@Suppress("LongParameterList", "TooManyFunctions")
class ReciteController(
    private val quranText: QuranTextRepository,
    progressEvents: ProgressEvents,
    val settings: ReciteSettings,
    val audioPlayer: AudioPlayer,
    private val speech: SpeechPreparation,
    inputFactory: () -> TrackerInput,
    private val scope: CoroutineScope,
    trackerFactory: TrackerFactory = DefaultTrackerFactory,
    recognitionDispatcher: CoroutineDispatcher = RecitationSession.newRecognitionDispatcher(),
    private val pageScript: ScriptId = ScriptId.V2,
    gradeSink: suspend (AyahRef, AyahGrade) -> Unit = { _, _ -> },
    private val revisionQueue: RevisionQueue = RevisionQueue { emptyList() },
    private val onOpenAyah: (AyahRef) -> Unit = {},
) {
    val session: RecitationSession =
        RecitationSession(
            quranText = quranText,
            referencePhonemes = speech.referencePhonemes,
            trackerFactory = trackerFactory,
            progressEvents = progressEvents,
            phonemeSourceFactory = { speech.newPhonemeSource() },
            inputFactory = inputFactory,
            scope = scope,
            recognitionDispatcher = recognitionDispatcher,
            nextAyahs = ::ayahsOnPageAfter,
            gradeSink = gradeSink,
        )

    private val mutableActive = MutableStateFlow(false)
    private val mutableRevising = MutableStateFlow(false)
    private val mutableRevisionRemaining = MutableStateFlow(0)

    // The ayahs still to revise in this run, in reading order; only touched on the controller's
    // scope.
    private var revisionQueueLeft: List<AyahRef> = emptyList()
    private var loadJob: Job? = null

    /** True from [reciteFrom] until [exit]; the Quran screen shows the recite bar meanwhile. */
    val isActive: StateFlow<Boolean> = mutableActive.asStateFlow()

    /** True while the user works through the ayahs due for revision. */
    val isRevising: StateFlow<Boolean> = mutableRevising.asStateFlow()

    /** How many due ayahs of this revision run are still ungraded. */
    val revisionRemaining: StateFlow<Int> = mutableRevisionRemaining.asStateFlow()

    /** Whether the speech files are ready; the bar waits on this. */
    val speechState: StateFlow<SpeechState> = speech.state

    init {
        // The settings page and the bar both write the settings; the session follows them.
        scope.launch { settings.speed.distinctUntilChanged().collect { session.setSpeed(it) } }
        scope.launch {
            settings.hifzEnabled.distinctUntilChanged().collect { session.setHifzEnabled(it) }
        }
    }

    /**
     * Loads the words from [ayah] to the end of its page and waits for the user to start the
     * microphone. A page that holds the end of one surah and the start of the next loads both.
     */
    fun reciteFrom(ayah: AyahRef) {
        mutableRevising.value = false
        load(ayah)
    }

    private fun load(ayah: AyahRef) {
        mutableActive.value = true
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            speech.prepare()
            session.load(ayahsFrom(ayah))
        }
    }

    /**
     * Starts a revision run at [ayah], the first ayah due: recite mode with the words hidden. The
     * due ayahs are read now; each grade given with [grade] moves on to the next one, opening it
     * through `onOpenAyah` when it is not already loaded.
     */
    fun reviseAt(ayah: AyahRef) {
        mutableRevising.value = true
        mutableActive.value = true
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            revisionQueueLeft = revisionQueue.dueAyahs().sorted()
            mutableRevisionRemaining.value = revisionQueueLeft.size
            settings.setHifzEnabled(true)
            session.setHifzEnabled(true)
            speech.prepare()
            session.load(ayahsFrom(ayah))
        }
    }

    /**
     * Grades the ayah that was just finished. During revision the run then moves to the next due
     * ayah after it (or the first one before it), and ends when none is left.
     */
    fun grade(grade: AyahGrade) {
        scope.launch {
            val graded = session.grade(grade) ?: return@launch
            if (mutableRevising.value) continueRevisionAfter(graded)
        }
    }

    private fun continueRevisionAfter(graded: AyahRef) {
        revisionQueueLeft = revisionQueueLeft - graded
        mutableRevisionRemaining.value = revisionQueueLeft.size
        val next = revisionQueueLeft.firstOrNull { it > graded } ?: revisionQueueLeft.firstOrNull()
        if (next == null) {
            mutableRevising.value = false
            return
        }
        // An ayah later on the loaded stretch needs no new load: the user is already on the way.
        val alreadyLoaded =
            next > graded &&
                session.words.value.any { it.key.surah == next.surah && it.key.ayah == next.ayah }
        if (alreadyLoaded) return
        onOpenAyah(next)
        load(next)
    }

    /** Waits until the last [reciteFrom] or [exit] has finished; for tests. */
    internal suspend fun joinPendingWork() {
        loadJob?.join()
    }

    // The ayahs after [last] on the first following page that has any. A long ayah can fill a whole
    // page, so the page right after the one of [last] may hold nothing new. An empty list means the
    // Quran has ended.
    private suspend fun ayahsOnPageAfter(last: AyahRef): List<AyahRef> {
        val lastPage = quranText.pageCount(pageScript)
        var page = quranText.pageOf(last, pageScript) + 1
        while (page <= lastPage) {
            val following = quranText.ayahsOnPage(page, pageScript).filter { it > last }
            if (following.isNotEmpty()) return following
            page++
        }
        return emptyList()
    }

    private suspend fun ayahsFrom(ayah: AyahRef): List<AyahRef> {
        val page = quranText.pageOf(ayah, pageScript)
        return quranText.ayahsOnPage(page, pageScript).filter { it >= ayah }
    }

    /** Stops the microphone, forgets the loaded words and leaves recite mode. */
    fun exit() {
        mutableActive.value = false
        mutableRevising.value = false
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            session.unload()
            // Only after the session let go of the recogniser: closing it earlier would free the
            // native model under a running recognition. The model stays for a grace period so a
            // restart is instant; it is released when that ends, or at once on [onLowMemory].
            speech.releaseAfterGrace()
        }
    }

    /**
     * Frees the loaded speech model now when recite mode is not on, for the system's low-memory and
     * trim calls. While reciting it does nothing: the model is in use.
     */
    fun onLowMemory() {
        val previous = loadJob
        loadJob = scope.launch {
            previous?.join()
            if (!mutableActive.value) speech.release()
        }
    }

    // The settings are the single source of truth: the session follows them (see init), so a stale
    // first read of the store can never overwrite a choice the user just made.

    /** Sets the speed and keeps it for next time. */
    fun setSpeed(speed: Float) {
        scope.launch { settings.setSpeed(speed) }
    }

    /** Turns memorise mode (hidden words) on or off and keeps the choice. */
    fun setHideWords(hide: Boolean) {
        scope.launch { settings.setHifzEnabled(hide) }
    }
}

/**
 * The ayah of the word the user is on while reciting, null when not reciting. The Quran screen
 * turns to the page of this ayah, so a recitation that goes past the end of the page is followed.
 */
@Composable
fun rememberReciteCurrentAyah(controller: ReciteController): AyahRef? {
    val word by controller.session.currentWord.collectAsState()
    return word?.let { AyahRef(it.surah, it.ayah) }
}

/**
 * What went wrong with each flagged word while reciting, empty otherwise. The Quran screen opens a
 * card with this when the user taps a red or yellow word.
 */
@Composable
fun rememberReciteWordProblems(controller: ReciteController): Map<WordKey, WordProblem> {
    val problems by controller.session.wordProblems.collectAsState()
    return problems
}

/** The real tracker, used when the caller does not pass a factory. */
internal object DefaultTrackerFactory : TrackerFactory {
    override fun create(config: TrackerConfig): Tracker = AlignmentTracker(config)
}

/**
 * The word states for the page: empty while not reciting, otherwise a state for every loaded word
 * (hidden words included in memorise mode). Words outside the loaded range have no entry.
 */
@Composable
fun rememberReciteWordStates(controller: ReciteController): Map<WordKey, WordState> {
    val states by controller.session.wordStates.collectAsState()
    return states
}
