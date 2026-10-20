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

/**
 * The one object :app creates for reciting on the Quran page. It owns the [RecitationSession], the
 * speech preparation and the settings, and offers the three things the Quran screen needs:
 * [reciteFrom] for `onReciteFrom`, [ReciteBar] for `reciteBar` and [rememberReciteWordStates] for
 * `reciteWordStates`.
 *
 * Create it once per process (for example in `AwnServices`) with a [scope] that outlives the
 * screens, and call [prepareSpeech] at app start.
 */
@Suppress("LongParameterList") // Each argument is a collaborator that :app supplies.
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
        )

    private val mutableActive = MutableStateFlow(false)
    private var loadJob: Job? = null

    /** True from [reciteFrom] until [exit]; the Quran screen shows the recite bar meanwhile. */
    val isActive: StateFlow<Boolean> = mutableActive.asStateFlow()

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
     * Copies and parses the speech files and loads the speech model in the background. Call it at
     * app start; it returns at once.
     */
    fun prepareSpeech() = speech.prepare()

    /**
     * Loads the words from [ayah] to the end of its page and waits for the user to start the
     * microphone. A page that holds the end of one surah and the start of the next loads both.
     */
    fun reciteFrom(ayah: AyahRef) {
        mutableActive.value = true
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            speech.prepare()
            session.load(ayahsFrom(ayah))
        }
    }

    /** Waits until the last [reciteFrom] or [exit] has finished; for tests. */
    internal suspend fun joinPendingWork() {
        loadJob?.join()
    }

    private suspend fun ayahsFrom(ayah: AyahRef): List<AyahRef> {
        val page = quranText.pageOf(ayah, pageScript)
        return quranText.ayahsOnPage(page, pageScript).filter { it >= ayah }
    }

    /** Stops the microphone, forgets the loaded words and leaves recite mode. */
    fun exit() {
        mutableActive.value = false
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            session.unload()
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
