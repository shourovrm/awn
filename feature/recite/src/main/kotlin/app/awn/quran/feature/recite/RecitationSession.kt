package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.tracker.DurationCheck
import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.Tracker
import app.awn.quran.tracker.TrackerConfig
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.TrackerFactory
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordState
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ayahs [firstAyah] to [lastAyah] of one surah, both included. */
data class ReciteRange(val surah: Int, val firstAyah: Int, val lastAyah: Int) {
    init {
        require(firstAyah >= 1) { "The first ayah must be 1 or more but was $firstAyah." }
        require(lastAyah >= firstAyah) { "The last ayah $lastAyah is before the first $firstAyah." }
    }

    fun ayahs(): List<AyahRef> = (firstAyah..lastAyah).map { AyahRef(surah, it) }

    /** Moves the start; the end follows when it would fall before the new start. */
    fun withFirstAyah(ayah: Int): ReciteRange =
        copy(firstAyah = ayah, lastAyah = maxOf(lastAyah, ayah))

    /** Moves the end; the start follows when it would fall after the new end. */
    fun withLastAyah(ayah: Int): ReciteRange =
        copy(firstAyah = minOf(firstAyah, ayah), lastAyah = ayah)
}

enum class RecitationPhase {
    /** Nothing is being heard; either nothing is loaded yet or the position was reset. */
    Idle,
    Listening,
    Stopped,

    /** The range has no reference text, or the microphone could not be opened. */
    Error,
}

/** The last thing worth telling the user; the screen turns it into a sentence. */
sealed interface ReciteStatus {
    val ayah: Int
    val wordPosition: Int

    data class Skipped(override val ayah: Int, override val wordPosition: Int) : ReciteStatus

    /** Extra words were heard after this word. */
    data class Inserted(override val ayah: Int, override val wordPosition: Int) : ReciteStatus

    /** The tracker lost the place and found it again at this word. */
    data class Anchored(override val ayah: Int, override val wordPosition: Int) : ReciteStatus
}

/** The first letter of a hidden word, shown because the user asked for a hint. */
data class ReciteHint(val key: WordKey, val letter: String)

/**
 * Follows one recitation: audio from [inputFactory] goes through the phoneme source into the
 * tracker on a thread of its own, and the results appear in the state flows below.
 *
 * Call [load] first, then [start] and [stop] as often as needed; [reset] forgets what was heard.
 * The tracker is touched only while holding [lock], so [setSpeed] is safe from any thread.
 */
// One screen's whole behaviour is behind these entry points, and every constructor argument is a
// separate collaborator that :app supplies.
@Suppress("TooManyFunctions", "LongParameterList")
class RecitationSession(
    private val quranText: QuranTextRepository,
    private val referencePhonemes: ReferencePhonemes,
    private val trackerFactory: TrackerFactory,
    private val progressEvents: ProgressEvents,
    private val phonemeSourceFactory: () -> PhonemeSource,
    private val inputFactory: () -> TrackerInput,
    private val scope: CoroutineScope,
    private val recognitionDispatcher: CoroutineDispatcher = newRecognitionDispatcher(),
    initialSpeed: Float = DEFAULT_SPEED,
) {
    private val lock = Any()

    private val mutablePhase = MutableStateFlow(RecitationPhase.Idle)
    private val mutableSpeed = MutableStateFlow(initialSpeed.coerceIn(0f, 1f))
    private val mutableHifz = MutableStateFlow(false)
    private val mutableWords = MutableStateFlow<List<Word>>(emptyList())
    private val mutableWordStates = MutableStateFlow<Map<WordKey, WordState>>(emptyMap())
    private val mutableCurrentWord = MutableStateFlow<WordKey?>(null)
    private val mutableStatus = MutableStateFlow<ReciteStatus?>(null)
    private val mutableHint = MutableStateFlow<ReciteHint?>(null)
    private val mutableSkippedWords = MutableStateFlow<Set<WordKey>>(emptySet())
    private val mutableReviews = MutableStateFlow<Map<Int, List<WordReview>>>(emptyMap())

    val phase: StateFlow<RecitationPhase> = mutablePhase.asStateFlow()
    val speed: StateFlow<Float> = mutableSpeed.asStateFlow()
    val hifzEnabled: StateFlow<Boolean> = mutableHifz.asStateFlow()

    /** The words of the loaded range in reading order, for drawing. */
    val words: StateFlow<List<Word>> = mutableWords.asStateFlow()

    /** How each word is drawn; in hifz mode unconfirmed words are [WordState.Hidden]. */
    val wordStates: StateFlow<Map<WordKey, WordState>> = mutableWordStates.asStateFlow()

    /** The first word not yet confirmed or red (wrong or skipped), null when the range is done. */
    val currentWord: StateFlow<WordKey?> = mutableCurrentWord.asStateFlow()
    val status: StateFlow<ReciteStatus?> = mutableStatus.asStateFlow()
    val hint: StateFlow<ReciteHint?> = mutableHint.asStateFlow()

    /**
     * The words the tracker reported as never heard. [WordState] has no skipped value, so the
     * screen reads this set to draw them in the skipped colour.
     */
    val skippedWords: StateFlow<Set<WordKey>> = mutableSkippedWords.asStateFlow()

    /** The skipped and wrong words of each ayah number that has any, in reading order. */
    val reviews: StateFlow<Map<Int, List<WordReview>>> = mutableReviews.asStateFlow()

    private var tracker: Tracker? = null
    private var wordKeys: List<WordKey> = emptyList()
    private var wordTextByKey: Map<WordKey, String> = emptyMap()
    private var trackerStates: Array<WordState> = emptyArray()
    private var durationFindings: Map<WordKey, List<DurationCheck>> = emptyMap()
    private var substitutionFindings: Map<WordKey, List<TrackerEvent.Substitution>> = emptyMap()
    private var skippedIndexes: MutableSet<Int> = mutableSetOf()
    private var greenAyahsReported: MutableSet<AyahRef> = mutableSetOf()
    private var hifzAyahsReported: MutableSet<AyahRef> = mutableSetOf()

    /** Why the last run ended in [RecitationPhase.Error], for diagnostics and tests. */
    @Volatile
    var lastFailure: Exception? = null
        private set

    private var recognitionJob: Job? = null
    private var activeInput: TrackerInput? = null

    suspend fun chapters(): List<Chapter> = quranText.chapters()

    /** Loads the text and reference of [range], replacing whatever was loaded before. */
    suspend fun load(range: ReciteRange) {
        stopAndWait()
        // Reading the reference phonemes can parse a large file the first time, so it must not
        // run on the caller's thread, which is the UI thread in the app.
        val loaded = withContext(Dispatchers.Default) { readRange(range) }
        if (loaded == null) {
            synchronized(lock) { clearLoaded() }
            mutablePhase.value = RecitationPhase.Error
            return
        }
        val (referenceWords, displayWords) = loaded
        val newTracker = trackerFactory.create(TrackerConfig(mutableSpeed.value))
        newTracker.load(referenceWords, startIndex = 0)
        synchronized(lock) {
            tracker = newTracker
            wordKeys = referenceWords.map {
                WordKey(it.key.surah, it.key.ayah, it.key.wordPosition)
            }
            wordTextByKey = displayWords.associate { it.key to it.textUthmani }
            trackerStates = Array(wordKeys.size) { WordState.Neutral }
            skippedIndexes = mutableSetOf()
            greenAyahsReported = mutableSetOf()
            hifzAyahsReported = mutableSetOf()
            mutableWords.value = displayWords
            mutableStatus.value = null
            mutableHint.value = null
            publishStates()
        }
        mutablePhase.value = RecitationPhase.Idle
    }

    /**
     * Returns the reference and display words of [range], or null when an ayah has no reference.
     */
    private suspend fun readRange(range: ReciteRange): Pair<List<ReferenceWord>, List<Word>>? {
        val referenceWords = mutableListOf<ReferenceWord>()
        val displayWords = mutableListOf<Word>()
        for (ayah in range.ayahs()) {
            val reference = referencePhonemes.forAyah(ayah.surah, ayah.ayah)
            if (reference.isEmpty()) return null
            referenceWords += reference
            displayWords += quranText.wordsOfAyah(ayah)
        }
        return referenceWords to displayWords
    }

    /**
     * Starts following the recitation. Results of an earlier run stay on screen until this call,
     * which clears them; does nothing while already listening.
     */
    fun start() {
        if (mutablePhase.value == RecitationPhase.Listening || tracker == null) return
        val previousRun = recognitionJob
        mutablePhase.value = RecitationPhase.Listening
        recognitionJob =
            scope.launch(recognitionDispatcher) {
                previousRun?.join()
                if (mutablePhase.value != RecitationPhase.Listening) return@launch
                synchronized(lock) { if (hasResults()) clearResults() }
                val input = inputFactory()
                activeInput = input
                recognise(input)
            }
    }

    /** Closes the audio input; what was heard so far stays on screen. */
    fun stop() {
        (activeInput as? AutoCloseable)?.close()
        if (mutablePhase.value == RecitationPhase.Listening) {
            mutablePhase.value = RecitationPhase.Stopped
        }
    }

    /** Stops and forgets everything heard, keeping the loaded range. */
    suspend fun reset() {
        stopAndWait()
        synchronized(lock) { clearResults() }
        mutablePhase.value = RecitationPhase.Idle
    }

    /** Must be called with [lock] held. */
    private fun clearResults() {
        tracker?.reset()
        trackerStates.fill(WordState.Neutral)
        skippedIndexes.clear()
        durationFindings = emptyMap()
        substitutionFindings = emptyMap()
        greenAyahsReported.clear()
        hifzAyahsReported.clear()
        mutableStatus.value = null
        mutableHint.value = null
        publishStates()
    }

    private fun hasResults(): Boolean =
        trackerStates.any { it != WordState.Neutral } || mutableStatus.value != null

    /** Sets how eager the tracker is, 0 patient to 1 eager; applies to the next audio heard. */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0f, 1f)
        mutableSpeed.value = clamped
        synchronized(lock) { tracker?.setConfig(TrackerConfig(clamped)) }
    }

    /** Turns hiding on or off; words already confirmed stay visible. */
    fun setHifzEnabled(enabled: Boolean) {
        synchronized(lock) {
            mutableHifz.value = enabled
            if (!enabled) mutableHint.value = null
            publishStates()
        }
    }

    /** Reveals the first letter of the next hidden word; only meaningful in hifz mode. */
    @Suppress("ReturnCount") // Each guard is one reason there is nothing to hint.
    fun requestHint() {
        synchronized(lock) {
            if (!mutableHifz.value) return
            val key = mutableCurrentWord.value ?: return
            val letter = wordTextByKey[key]?.let(HifzRules::firstLetter) ?: return
            mutableHint.value = ReciteHint(key, letter)
        }
    }

    private suspend fun stopAndWait() {
        stop()
        recognitionJob?.join()
        recognitionJob = null
        activeInput = null
    }

    private suspend fun recognise(input: TrackerInput) {
        val source =
            try {
                phonemeSourceFactory()
            } catch (problem: IllegalStateException) {
                // The speech files could not be put in place; the screen shows the error line.
                failListening(problem)
                (input as? AutoCloseable)?.close()
                return
            }
        try {
            while (true) {
                val block = input.nextBlock() ?: break
                handle(source.accept(block))
            }
            handle(source.finish())
        } catch (problem: SecurityException) {
            failListening(problem)
        } catch (problem: IllegalStateException) {
            failListening(problem)
        } finally {
            source.close()
            (input as? AutoCloseable)?.close()
            if (mutablePhase.value == RecitationPhase.Listening) {
                mutablePhase.value = RecitationPhase.Stopped
            }
        }
    }

    private fun failListening(problem: Exception) {
        // The microphone is the only expected cause; the screen shows one message for it.
        lastFailure = problem
        mutablePhase.value = RecitationPhase.Error
    }

    private suspend fun handle(phonemes: List<PhonemeEvent>) {
        if (phonemes.isEmpty()) return
        val notifications =
            synchronized(lock) {
                val activeTracker = tracker ?: return
                val events = activeTracker.onPhonemes(phonemes)
                events.forEach(::apply)
                publishStates()
                collectAyahNotifications(events)
            }
        notifications.forEach { notification -> notification() }
    }

    private fun apply(event: TrackerEvent) {
        when (event) {
            is TrackerEvent.WordChanged -> trackerStates[event.index] = event.state
            is TrackerEvent.Skipped -> {
                skippedIndexes += event.index
                trackerStates[event.index] = WordState.Wrong
                mutableStatus.value = statusFor(event.index, ReciteStatus::Skipped)
            }
            is TrackerEvent.Inserted ->
                mutableStatus.value = statusFor(event.afterIndex, ReciteStatus::Inserted)
            is TrackerEvent.Anchored ->
                mutableStatus.value = statusFor(event.index, ReciteStatus::Anchored)
            is TrackerEvent.Duration -> {
                val key = wordKeys[event.check.wordIndex]
                durationFindings =
                    durationFindings + (key to durationFindings[key].orEmpty() + event.check)
            }
            is TrackerEvent.Substitution -> {
                val key = wordKeys[event.wordIndex]
                substitutionFindings =
                    substitutionFindings + (key to substitutionFindings[key].orEmpty() + event)
            }
        }
    }

    private fun statusFor(index: Int, create: (Int, Int) -> ReciteStatus): ReciteStatus {
        val key = wordKeys[index]
        return create(key.ayah, key.wordPosition)
    }

    /** Must be called with [lock] held. */
    private fun publishStates() {
        val hifz = mutableHifz.value
        val states = LinkedHashMap<WordKey, WordState>()
        wordKeys.forEachIndexed { index, key ->
            states[key] = HifzRules.displayState(trackerStates[index], hifz)
        }
        mutableWordStates.value = states
        val currentIndex = trackerStates.indices.firstOrNull { !isResolved(it) }
        val current = currentIndex?.let { wordKeys[it] }
        mutableCurrentWord.value = current
        val hintedKey = mutableHint.value?.key
        if (hintedKey != null && hintedKey != current) mutableHint.value = null
        mutableSkippedWords.value = skippedIndexes.map { wordKeys[it] }.toSet()
        mutableReviews.value =
            ReviewRules.reviewsByAyah(
                wordKeys,
                trackerStates.toList(),
                skippedIndexes,
                durationFindings,
                substitutionFindings,
            )
    }

    // The tracker counts a red word as passed (`Tracker.currentIndex`), so the screen moves on
    // past it too; a skipped word is always red as well.
    private fun isResolved(index: Int): Boolean =
        trackerStates[index] == WordState.Confirmed ||
            trackerStates[index] == WordState.Wrong ||
            index in skippedIndexes

    /**
     * Finds the ayahs that just became complete. An ayah is green when every word is confirmed. In
     * hifz mode it also counts as recited once every word is confirmed or skipped, and then the
     * skips are reported. Must be called with [lock] held; the returned calls run outside it.
     */
    private fun collectAyahNotifications(events: List<TrackerEvent>): List<suspend () -> Unit> {
        val touchedAyahs = events.map { ayahOfIndex(it) }.distinct()
        val notifications = mutableListOf<suspend () -> Unit>()
        for (ayah in touchedAyahs) {
            val indexes = wordKeys.indices.filter { wordKeys[it].ayah == ayah.ayah }
            val allConfirmed = indexes.all { trackerStates[it] == WordState.Confirmed }
            if (allConfirmed && greenAyahsReported.add(ayah)) {
                notifications += { progressEvents.ayahTrackedGreen(ayah) }
            }
            val skipCount = indexes.count {
                trackerStates[it] == WordState.Wrong || it in skippedIndexes
            }
            val allResolved = indexes.all(::isResolved)
            if (mutableHifz.value && allResolved && hifzAyahsReported.add(ayah)) {
                notifications += { progressEvents.hifzCompleted(ayah, skipCount) }
            }
        }
        return notifications
    }

    private fun ayahOfIndex(event: TrackerEvent): AyahRef {
        val index =
            when (event) {
                is TrackerEvent.WordChanged -> event.index
                is TrackerEvent.Skipped -> event.index
                is TrackerEvent.Inserted -> event.afterIndex
                is TrackerEvent.Anchored -> event.index
                is TrackerEvent.Duration -> event.check.wordIndex
                is TrackerEvent.Substitution -> event.wordIndex
            }
        return AyahRef(wordKeys[index].surah, wordKeys[index].ayah)
    }

    private fun clearLoaded() {
        tracker = null
        wordKeys = emptyList()
        wordTextByKey = emptyMap()
        trackerStates = emptyArray()
        mutableWords.value = emptyList()
        mutableWordStates.value = emptyMap()
        mutableCurrentWord.value = null
        mutableReviews.value = emptyMap()
        mutableSkippedWords.value = emptySet()
    }

    companion object {
        const val DEFAULT_SPEED = 0.5f

        /** One thread that does nothing but wait for audio and run the tracker. */
        fun newRecognitionDispatcher(): CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "awn-recite").apply { isDaemon = true }
                }
                .asCoroutineDispatcher()
    }
}
