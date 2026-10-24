package app.awn.quran.feature.recite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.MyWords
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.player.AudioPlayer
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.AlignmentTracker
import app.awn.quran.tracker.Tracker
import app.awn.quran.tracker.TrackerConfig
import app.awn.quran.tracker.TrackerFactory
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** Whether [reciterId] has word timings for the ayah, so a single word can be played. */
    internal val hasWordTimings: suspend (reciterId: String, AyahRef) -> Boolean = { _, _ ->
        false
    },
    /**
     * The reciter whose voice plays a single word, given the recite reciter. The app passes the
     * Word audio choice of Settings; :feature:recite cannot see :feature:read, which holds the
     * rule.
     */
    internal val wordReciterId: suspend (reciterId: String) -> String = { it },
    /** The user's own word list, or null when the course is not available. */
    val myWords: MyWords? = null,
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
    private val mutableFocusedWord = MutableStateFlow<WordKey?>(null)
    private val mutableListeningOnward = MutableStateFlow(false)

    // The ayahs still to revise in this run, in reading order; only touched on the controller's
    // scope.
    private var revisionQueueLeft: List<AyahRef> = emptyList()
    private var loadJob: Job? = null

    // The latest write of the hide-words choice, so a test can wait until it is stored.
    private var hideWordsSaveJob: Job? = null

    // The last ayah the player reported since [listenOnward], and whether the audio has ended since
    // it started. Together they say where reciting goes on.
    @Volatile private var lastListenedAyah: AyahRef? = null
    @Volatile private var onwardAudioHasStarted = false
    @Volatile private var onwardAudioHasEnded = false

    // Set when this controller stopped the audio itself: the player still reports Playing for a
    // moment after stop(), and that last report must not read as the user starting to listen.
    @Volatile private var ignoreAudioUntilSilent = false

    // True from [practiceFrom] or [reviseAt] until the run ends. While it is set, the hide-words
    // choice belongs to the run and the saved setting does not reach the session. Volatile because
    // the settings collector reads it on the scope while the screen writes it.
    @Volatile private var runActive: Boolean = false

    // The last ayah of a run started with [practiceAyahs]; null for every other run.
    @Volatile private var lastAyahOfBoundedRun: AyahRef? = null

    /**
     * True from [reciteFrom], [practiceFrom] or [reviseAt] until [exit]; the recite bar shows
     * meanwhile.
     */
    val isActive: StateFlow<Boolean> = mutableActive.asStateFlow()

    /** True while the user works through the ayahs due for revision. */
    val isRevising: StateFlow<Boolean> = mutableRevising.asStateFlow()

    /** How many due ayahs of this revision run are still ungraded. */
    val revisionRemaining: StateFlow<Int> = mutableRevisionRemaining.asStateFlow()

    /**
     * The flagged word the user is looking at in the review card, null when none. The Quran screen
     * turns to it and rings it, and Listen plays it. It is cleared when the card closes, when
     * recite mode ends, and when the word stops being flagged (a new run clears the results).
     */
    val focusedWord: StateFlow<WordKey?> = mutableFocusedWord.asStateFlow()

    /**
     * True from [listenOnward] until the user goes back to reciting with [resumeReciting], stops
     * with [stopListening], or leaves recite mode. The audio may have ended by itself meanwhile;
     * the microphone is off all that time.
     */
    val isListeningOnward: StateFlow<Boolean> = mutableListeningOnward.asStateFlow()

    /** Whether the speech files are ready; the bar waits on this. */
    val speechState: StateFlow<SpeechState> = speech.state

    init {
        // The settings page and the bar both write the speed; the session follows it.
        scope.launch { settings.speed.distinctUntilChanged().collect { session.setSpeed(it) } }
        scope.launch { audioPlayer.state.collect(::followAudioWhileListeningOnward) }
        scope.launch {
            combine(session.reviews, session.wordProblems, ::flaggedReviews).collect { flagged ->
                val focused = mutableFocusedWord.value
                if (focused != null && flagged.none { it.key == focused }) clearFocus()
            }
        }
    }

    /**
     * Loads the words from [ayah] to the end of its page and waits for the user to start the
     * microphone. A page that holds the end of one surah and the start of the next loads both.
     */
    fun reciteFrom(ayah: AyahRef) {
        // The microphone would hear the reciter, so audio never plays under a new recitation.
        stopAudioForNewRun()
        mutableRevising.value = false
        runActive = false
        load(ayah) {
            // Read here, once per run, and not followed while reciting: a follower fed by the
            // store echoed the bar's own writes back, and its first read, taken before the user's
            // tap, switched the words back.
            session.setHifzEnabled(settings.hifzEnabled.first())
        }
    }

    /**
     * Starts a Hifz practice run at [ayah]: recite mode with the words hidden when [hideWords] is
     * true and shown when it is false. The choice lasts for this run only; the saved setting is not
     * changed.
     */
    fun practiceFrom(ayah: AyahRef, hideWords: Boolean) {
        stopAudioForNewRun()
        mutableRevising.value = false
        runActive = true
        load(ayah) { session.setHifzEnabled(hideWords) }
    }

    /**
     * Starts a run that loads exactly [ayahs] (in reading order, within one surah) and nothing
     * after them, so a short passage such as a dua is recited on its own. The words are hidden when
     * [hideWords] is true and shown when it is false, for this run only like [practiceFrom].
     */
    fun practiceAyahs(ayahs: List<AyahRef>, hideWords: Boolean) {
        require(ayahs.isNotEmpty()) { "A bounded run needs at least one ayah." }
        stopAudioForNewRun()
        mutableRevising.value = false
        runActive = true
        load(ayahs.first(), boundedAyahs = ayahs) { session.setHifzEnabled(hideWords) }
    }

    /** Points the review at [key], a flagged word: the screen shows it and Listen plays it. */
    fun focusWord(key: WordKey) {
        mutableFocusedWord.value = key
    }

    /** Ends the review focus; Listen goes back to the next word to recite. */
    fun clearFocus() {
        mutableFocusedWord.value = null
    }

    // [beforeLoad] runs after the previous load has finished and before the words are loaded, so
    // a run can set the session's hide-words choice before its first word is shown.
    // [boundedAyahs], when given, are the only ayahs of the run: the session neither loads the rest
    // of the page nor extends past the last of them.
    private fun load(
        ayah: AyahRef,
        boundedAyahs: List<AyahRef>? = null,
        beforeLoad: suspend () -> Unit = {},
    ) {
        lastAyahOfBoundedRun = boundedAyahs?.last()
        clearFocus()
        mutableActive.value = true
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            // Before the settings read of [beforeLoad], so the model starts loading at once.
            speech.prepare()
            beforeLoad()
            session.load(boundedAyahs ?: ayahsFrom(ayah))
        }
    }

    /**
     * Starts a revision run at [ayah], the first ayah due: recite mode with the words hidden for
     * this run only. The due ayahs are read now and [selectRevisionQueue] keeps at most [limit] of
     * them; each grade given with [grade] moves on to the next one, opening it through `onOpenAyah`
     * when it is not already loaded.
     */
    fun reviseAt(ayah: AyahRef, limit: Int? = null) {
        stopAudioForNewRun()
        mutableRevising.value = true
        runActive = true
        load(ayah) {
            revisionQueueLeft = selectRevisionQueue(revisionQueue.dueAyahs(), ayah, limit)
            mutableRevisionRemaining.value = revisionQueueLeft.size
            session.setHifzEnabled(true)
        }
    }

    /**
     * The ayahs one revision run covers, in reading order. Without a [limit] that is every due
     * ayah. With a limit it is [limit] ayahs counted from [start] and wrapping to the beginning of
     * the Quran: [start] itself comes first even when it is not due. The Today row offers a day's
     * worth of ayahs rather than every due one, so a run is capped and the rest wait for the next.
     */
    private fun selectRevisionQueue(
        dueAyahs: List<AyahRef>,
        start: AyahRef,
        limit: Int?,
    ): List<AyahRef> {
        val readingOrder = dueAyahs.sorted()
        if (limit == null) return readingOrder
        require(limit >= 1) { "A revision run covers at least one ayah, got $limit." }
        val others = readingOrder.filter { it != start }
        val afterStart = others.filter { it > start }
        val beforeStart = others.filter { it < start }
        return (listOf(start) + afterStart + beforeStart).take(limit).sorted()
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

    /**
     * Plays the reciter from the ayah the user is on to the end of its surah, with the repeat
     * setting of the player, and keeps the microphone off. It goes on until the user presses the
     * microphone ([resumeReciting]) or stops it ([stopListening]).
     */
    fun listenOnward() {
        if (session.words.value.isEmpty()) return
        onwardAudioHasStarted = false
        onwardAudioHasEnded = false
        ignoreAudioUntilSilent = false
        mutableListeningOnward.value = true
        pauseMicrophone()
        runAfterPendingWork {
            // Found after the pending steps: a resume before this has moved the next word.
            val ayah = ayahToListenTo()
            if (ayah == null) {
                mutableListeningOnward.value = false
                return@runAfterPendingWork
            }
            lastListenedAyah = ayah
            // The tracker is not asked about the ayahs played, whatever the microphone hears later.
            session.continueAt(ayah)
            audioPlayer.play(settings.reciterId.first(), ayah, to = null)
            // Counted from the play call, not from a Playing state: a short ayah can go from
            // Playing to Idle between two looks at the player.
            onwardAudioHasStarted = true
        }
    }

    /**
     * Stops the audio and starts the microphone again where the listening left off: at the ayah
     * that was playing, or after the last one played when the audio had ended. The ayahs only
     * listened to are not marked as left out.
     */
    fun resumeReciting() {
        mutableListeningOnward.value = false
        ignoreAudioUntilSilent = true
        noteAudioEndedBeforeStopping()
        stopAudio()
        runAfterPendingWork {
            settleContinuationPoint()
            session.start()
        }
    }

    /** Stops the audio and leaves the microphone off; the next start goes on where it left off. */
    fun stopListening() {
        mutableListeningOnward.value = false
        ignoreAudioUntilSilent = true
        noteAudioEndedBeforeStopping()
        stopAudio()
        runAfterPendingWork { settleContinuationPoint() }
    }

    // Reads the player as it is before our own stop() makes it Idle: Idle then, after the play
    // call,
    // means the audio ran out. A state flow answers at once, so this runs before the caller goes
    // on.
    private fun noteAudioEndedBeforeStopping() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val playback = audioPlayer.state.first()
            // The collector may have missed the last ayah too, so the player's own answer wins.
            if (playback is PlaybackState.Playing) lastListenedAyah = playback.ayah
            if (playback is PlaybackState.Paused) lastListenedAyah = playback.ayah
            if (onwardAudioHasStarted && playback.isSilent()) onwardAudioHasEnded = true
        }
    }

    // Stops the audio for a new run and forgets any listening that was going on.
    private fun stopAudioForNewRun() {
        mutableListeningOnward.value = false
        onwardAudioHasStarted = false
        onwardAudioHasEnded = false
        lastListenedAyah = null
        stopAudio()
    }

    // The player goes on reporting Playing for a moment after stop(). Those reports, and the Idle
    // that ends them, are ours and must not read as the user starting or ending a listening. When
    // nothing was playing there is no Idle to wait for, so the flag is lifted at once.
    private fun stopAudio() {
        ignoreAudioUntilSilent = true
        audioPlayer.stop()
        if (isSilentNow()) ignoreAudioUntilSilent = false
    }

    // The player's own answer, which a collector that lags behind cannot give.
    private fun isSilentNow(): Boolean {
        var isSilent = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            isSilent = audioPlayer.state.first().isSilent()
        }
        return isSilent
    }

    private fun PlaybackState.isSilent(): Boolean =
        this is PlaybackState.Idle || this is PlaybackState.Error

    // Tells the session where the next start goes on. An ayah that is not loaded (the audio ran on
    // past the loaded words) is loaded fresh, which also ends the old marks: the page has moved on.
    private suspend fun settleContinuationPoint() {
        val ayah = continuationAyah() ?: return
        // Used once: a later listening of another kind must not go back to this ayah.
        lastListenedAyah = null
        val isLoaded =
            session.words.value.any { it.key.surah == ayah.surah && it.key.ayah == ayah.ayah }
        if (isLoaded) {
            session.continueAt(ayah)
        } else {
            session.load(ayahsFrom(ayah))
        }
    }

    // The ayah where reciting goes on after listening: the one playing when the audio was stopped,
    // or the one after the last played when the audio had ended. Null when nothing was played.
    private suspend fun continuationAyah(): AyahRef? {
        val last = lastListenedAyah ?: return null
        return if (onwardAudioHasEnded) nextAyahAfter(last) else last
    }

    internal suspend fun nextAyahAfter(ayah: AyahRef): AyahRef? {
        val page = quranText.pageOf(ayah, pageScript)
        val onSamePage = quranText.ayahsOnPage(page, pageScript).firstOrNull { it > ayah }
        return onSamePage ?: ayahsOnPageAfter(ayah).firstOrNull()
    }

    // Follows the audio whoever started it: the Listen button, the ayah bar or a word. Audio that
    // the Listen button did not start only counts once it plays an ayah past the one the reciter
    // is on; the review card plays the ayah of a mistake, and reciting then goes on where it was.
    private fun followAudioWhileListeningOnward(playback: PlaybackState) {
        if (ignoreAudioUntilSilent) {
            if (playback.isSilent()) ignoreAudioUntilSilent = false
            return
        }
        if (isFollowingAudio(playback)) noteAudioState(playback)
    }

    private fun isFollowingAudio(playback: PlaybackState): Boolean =
        mutableActive.value && (mutableListeningOnward.value || beginsListeningOnward(playback))

    private fun noteAudioState(playback: PlaybackState) {
        when (playback) {
            is PlaybackState.Playing -> noteListenedAyah(playback.ayah)
            is PlaybackState.Paused -> noteListenedAyah(playback.ayah)
            is PlaybackState.Downloading -> onwardAudioHasStarted = true
            is PlaybackState.Idle,
            is PlaybackState.Error -> if (onwardAudioHasStarted) endListeningOnward()
        }
    }

    // Audio the Listen button did not start counts as listening only when it plays an ayah past the
    // one the reciter is on, and the player still plays it now: a collector that lags can deliver
    // a Playing report for audio that has already stopped.
    private fun beginsListeningOnward(playback: PlaybackState): Boolean {
        val ayah = playedAyahOf(playback)
        val isNewListening = ayah != null && isPastTheReciter(ayah) && !isSilentNow()
        if (isNewListening) {
            onwardAudioHasStarted = false
            onwardAudioHasEnded = false
            mutableListeningOnward.value = true
        }
        return isNewListening
    }

    private fun playedAyahOf(playback: PlaybackState): AyahRef? =
        when (playback) {
            is PlaybackState.Playing -> playback.ayah
            is PlaybackState.Paused -> playback.ayah
            else -> null
        }

    private fun isPastTheReciter(ayah: AyahRef): Boolean {
        val current = session.currentWord.value ?: return true
        return ayah > AyahRef(current.surah, current.ayah)
    }

    private fun noteListenedAyah(ayah: AyahRef) {
        lastListenedAyah = ayah
        onwardAudioHasStarted = true
    }

    // The audio ran out by itself: the microphone button goes back to its normal job, and a plain
    // start goes on after the last ayah played.
    private fun endListeningOnward() {
        onwardAudioHasEnded = true
        mutableListeningOnward.value = false
        runAfterPendingWork { settleContinuationPoint() }
    }

    // Runs [work] after the load, exit or listening step before it, so the steps stay in order.
    private fun runAfterPendingWork(work: suspend () -> Unit) {
        val previous = loadJob
        loadJob = scope.launch {
            previous?.join()
            work()
        }
    }

    /**
     * Waits until the last [reciteFrom] or [exit] has finished and the last hide-words choice is
     * stored; for tests.
     */
    internal suspend fun joinPendingWork() {
        loadJob?.join()
        hideWordsSaveJob?.join()
    }

    // The ayahs after [last] on the first following page that has any. A long ayah can fill a whole
    // page, so the page right after the one of [last] may hold nothing new. An empty list means the
    // Quran has ended.
    private suspend fun ayahsOnPageAfter(last: AyahRef): List<AyahRef> {
        val boundary = lastAyahOfBoundedRun
        if (boundary != null && last >= boundary) return emptyList()
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
        mutableListeningOnward.value = false
        onwardAudioHasStarted = false
        lastListenedAyah = null
        clearFocus()
        mutableActive.value = false
        mutableRevising.value = false
        lastAyahOfBoundedRun = null
        val endedRun = runActive
        runActive = false
        val previous = loadJob
        loadJob = scope.launch {
            previous?.cancelAndJoin()
            // Before the unload: the session has no word to publish states for once it is empty.
            if (endedRun) session.setHifzEnabled(settings.hifzEnabled.first())
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

    /**
     * Turns memorise mode (hidden words) on or off. During a practice or revision run the choice
     * lasts for that run only; outside a run it is kept as the saved setting.
     */
    fun setHideWords(hide: Boolean) {
        // The session changes at once. Waiting for the saved value to come back made two quick taps
        // read the same old state and send the same choice, so the second tap seemed to do nothing.
        // The saved value is only the next run's starting point.
        session.setHifzEnabled(hide)
        if (!runActive) hideWordsSaveJob = scope.launch { settings.setHifzEnabled(hide) }
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

/**
 * The first letter of the next word when the user asked for it with "Show next word": the page
 * draws it inside that word's hidden box. Empty when no letter is shown.
 */
@Composable
fun rememberReciteHintLetters(controller: ReciteController): Map<WordKey, String> {
    val hint by controller.session.hint.collectAsState()
    return remember(hint) {
        val shown = hint
        if (shown == null) emptyMap() else mapOf(shown.key to shown.letter)
    }
}
