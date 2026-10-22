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
import app.awn.quran.tracker.WordProblem
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

/** The first letter of a hidden word, shown inside the word's hidden box on the page. */
data class ReciteHint(val key: WordKey, val letter: String)

/**
 * Follows one recitation: audio from [inputFactory] goes through the phoneme source into the
 * tracker on a thread of its own, and the results appear in the state flows below.
 *
 * Call [load] first, then [start] and [stop] as often as needed; [reset] forgets what was heard.
 * While the user recites, the loaded words grow: when fewer than [EXTEND_BELOW_WORDS] words are
 * left, [nextAyahs] supplies the ayahs after the last loaded one (across a page or a surah) and
 * they are appended to the tracker without a reset. An empty answer means the Quran has ended. The
 * tracker is touched only while holding [lock], so [setSpeed] is safe from any thread.
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
    private val nextAyahs: suspend (after: AyahRef) -> List<AyahRef> = { emptyList() },
    private val gradeSink: suspend (AyahRef, AyahGrade) -> Unit = { _, _ -> },
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
    private val mutableGradePrompt = MutableStateFlow<GradePrompt?>(null)
    private val mutableWordProblems = MutableStateFlow<Map<WordKey, WordProblem>>(emptyMap())
    private val mutableReviews = MutableStateFlow<Map<AyahRef, List<WordReview>>>(emptyMap())

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

    /** The ayah just finished in hide-words mode, waiting for [grade]; null when none waits. */
    val gradePrompt: StateFlow<GradePrompt?> = mutableGradePrompt.asStateFlow()

    /** What went wrong with each flagged word, for the card that opens on a tap. */
    val wordProblems: StateFlow<Map<WordKey, WordProblem>> = mutableWordProblems.asStateFlow()

    /** The skipped and wrong words of each ayah that has any, in reading order. */
    val reviews: StateFlow<Map<AyahRef, List<WordReview>>> = mutableReviews.asStateFlow()

    private var tracker: Tracker? = null
    private var wordKeys: List<WordKey> = emptyList()
    private var wordTextByKey: Map<WordKey, String> = emptyMap()
    private var trackerStates: Array<WordState> = emptyArray()
    private var durationFindings: Map<WordKey, List<DurationCheck>> = emptyMap()
    private var substitutionFindings: Map<WordKey, List<TrackerEvent.Substitution>> = emptyMap()
    private var skippedIndexes: MutableSet<Int> = mutableSetOf()
    private var greenAyahsReported: MutableSet<AyahRef> = mutableSetOf()
    private var hifzAyahsReported: MutableSet<AyahRef> = mutableSetOf()
    private var nothingToAppend = false

    // Set by [pauseForListening]: the next [start] continues the recitation instead of clearing it.
    @Volatile private var continuesOnNextStart = false

    // Set by [continueAt]: where the next continuing [start] picks up, instead of at the first
    // word not yet recited. Cleared by that start.
    @Volatile private var continuationAyah: AyahRef? = null
    private var revealedIndexes: MutableSet<Int> = mutableSetOf()

    // Words the user only heard played. They count as passed, are drawn without a mark, and are
    // never reported as left out.
    private var listenedIndexes: MutableSet<Int> = mutableSetOf()

    // The word where the last continuing start picked up, and whether the user's first confirmed
    // word after it is still to come. Both only matter for [withoutListenedWords].
    private var continuationIndex: Int? = null
    private var continuationExpectedAyah: AyahRef? = null
    private var awaitingFirstConfirmedWord = false
    private var userSkippedContinuationAyah = false

    /** Why the last run ended in [RecitationPhase.Error], for diagnostics and tests. */
    @Volatile
    var lastFailure: Exception? = null
        private set

    private var recognitionJob: Job? = null
    @Volatile private var activeInput: TrackerInput? = null

    suspend fun chapters(): List<Chapter> = quranText.chapters()

    /** Loads the text and reference of [range], replacing whatever was loaded before. */
    suspend fun load(range: ReciteRange) = load(range.ayahs())

    /**
     * Loads the text and reference of [ayahs], which must follow each other in reading order, and
     * replaces whatever was loaded before. They may cross from one surah into the next.
     */
    suspend fun load(ayahs: List<AyahRef>) {
        stopAndWait()
        // Reading the reference phonemes can parse a large file the first time, so it must not
        // run on the caller's thread, which is the UI thread in the app.
        val loaded =
            try {
                withContext(Dispatchers.Default) { readAyahs(ayahs) }
            } catch (problem: IllegalStateException) {
                // The speech files could not be prepared; the bar shows the error line.
                lastFailure = problem
                null
            }
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
            nothingToAppend = false
            continuesOnNextStart = false
            continuationAyah = null
            revealedIndexes = mutableSetOf()
            listenedIndexes = mutableSetOf()
            continuationIndex = null
            continuationExpectedAyah = null
            mutableGradePrompt.value = null
            mutableWords.value = displayWords
            mutableStatus.value = null
            mutableHint.value = null
            publishStates()
        }
        mutablePhase.value = RecitationPhase.Idle
    }

    /**
     * Returns the reference and display words of [ayahs], or null when an ayah has no reference.
     */
    private suspend fun readAyahs(ayahs: List<AyahRef>): Pair<List<ReferenceWord>, List<Word>>? {
        val referenceWords = mutableListOf<ReferenceWord>()
        val displayWords = mutableListOf<Word>()
        for (ayah in ayahs) {
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
                synchronized(lock) {
                    if (continuesOnNextStart) {
                        continueRecitation()
                    } else {
                        if (hasResults()) clearResults()
                        // A fresh run has nothing it only listened to.
                        listenedIndexes.clear()
                        continuationIndex = null
                    }
                    continuesOnNextStart = false
                    continuationAyah = null
                }
                val input = inputFactory()
                activeInput = input
                // A stop that came after the phase check above but before the line above found no
                // input to close, so the microphone would stay open and block here for good.
                if (mutablePhase.value != RecitationPhase.Listening) {
                    (input as? AutoCloseable)?.close()
                    return@launch
                }
                recognise(input)
            }
    }

    /**
     * Stops the microphone so it does not hear the reciter being played, and makes the next [start]
     * go on from the next expected word with every mark kept. A new [load] or [reset] starts fresh.
     */
    fun pauseForListening() {
        continuesOnNextStart = true
        stop()
    }

    /**
     * Makes the next continuing [start] (after [pauseForListening]) pick up at the first word of
     * [ayah] instead of at the first word not yet recited. The words in between were listened to,
     * not recited, so they are never reported as left out.
     */
    fun continueAt(ayah: AyahRef) {
        continuationAyah = ayah
    }

    /**
     * Tells the tracker where to go on. Must be called with [lock] held.
     *
     * With [continuationAyah] set, every word before that ayah that is not recited yet counts as
     * listened to. Without it the recitation goes on at the first word not yet recited.
     */
    private fun continueRecitation() {
        val ayah = continuationAyah
        val pickUpIndex =
            if (ayah == null) {
                firstOpenIndex() ?: wordKeys.size
            } else {
                wordKeys.indices.firstOrNull { ayahOf(it) >= ayah } ?: wordKeys.size
            }
        tracker?.continueFrom(pickUpIndex)
        if (ayah == null) return
        for (index in 0 until pickUpIndex) {
            if (!isResolved(index)) listenedIndexes += index
        }
        continuationIndex = pickUpIndex
        continuationExpectedAyah =
            wordKeys.getOrNull(pickUpIndex)?.let { AyahRef(it.surah, it.ayah) }
        awaitingFirstConfirmedWord = true
        userSkippedContinuationAyah = false
        publishStates()
    }

    /** Closes the audio input; what was heard so far stays on screen. */
    fun stop() {
        // The phase changes first: the recognition job checks it after publishing its input, so one
        // of the two sees the other and the microphone is always closed.
        if (mutablePhase.value == RecitationPhase.Listening) {
            mutablePhase.value = RecitationPhase.Stopped
        }
        (activeInput as? AutoCloseable)?.close()
    }

    /** Stops and forgets the loaded words as well as everything heard; the page draws normally. */
    suspend fun unload() {
        stopAndWait()
        synchronized(lock) { clearLoaded() }
        mutableStatus.value = null
        mutableHint.value = null
        mutablePhase.value = RecitationPhase.Idle
    }

    /** Stops and forgets everything heard, keeping the loaded range. */
    suspend fun reset() {
        stopAndWait()
        synchronized(lock) { clearResults() }
        continuesOnNextStart = false
        mutablePhase.value = RecitationPhase.Idle
    }

    /** Must be called with [lock] held. */
    private fun clearResults() {
        tracker?.reset()
        trackerStates.fill(WordState.Neutral)
        skippedIndexes.clear()
        revealedIndexes.clear()
        listenedIndexes.clear()
        continuationIndex = null
        continuationExpectedAyah = null
        mutableGradePrompt.value = null
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

    /**
     * Turns hiding on or off; words already confirmed stay visible. Words shown with "show next
     * word" or "show ayah", a pending grade and a hint belong to the hiding that was on, so they
     * are dropped: when hiding comes back the words are hidden again and the buttons start over.
     */
    fun setHifzEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (mutableHifz.value == enabled) return
            mutableHifz.value = enabled
            revealedIndexes.clear()
            mutableGradePrompt.value = null
            mutableHint.value = null
            publishStates()
        }
    }

    /**
     * Shows the next hidden word without the microphone, for silent self-testing. With
     * [showsFirstLetterFirst] the first tap shows only the word's first letter inside its hidden
     * box and the second tap on the same word shows the whole word. A revealed word counts as help:
     * the ayah is not reported as recited by itself and waits for a grade. A first letter alone is
     * a hint and does not count.
     */
    fun revealNextWord(showsFirstLetterFirst: Boolean = true) {
        val notifications =
            synchronized(lock) {
                val index = firstOpenIndex()
                if (!mutableHifz.value || index == null) return
                if (showsFirstLetterFirst && showFirstLetterOf(index)) return
                revealedIndexes += index
                mutableHint.value = null
                publishStates()
                collectAyahNotifications(listOf(ayahOf(index)))
            }
        runNotifications(notifications)
    }

    // Returns true when the letter was shown by this call, false when it was already shown or the
    // word has no letter, in which case the caller shows the whole word. Must be called with
    // [lock] held.
    private fun showFirstLetterOf(index: Int): Boolean {
        val key = wordKeys[index]
        val letter = wordTextByKey[key]?.let(HifzRules::firstLetter)
        val isNewLetter = letter != null && mutableHint.value?.key != key
        if (isNewLetter) mutableHint.value = ReciteHint(key, checkNotNull(letter))
        return isNewLetter
    }

    /**
     * Shows every hidden word of the ayah being recited, with the same rules as [revealNextWord].
     */
    fun revealAyah() {
        val notifications =
            synchronized(lock) {
                val index = firstOpenIndex()
                if (!mutableHifz.value || index == null) return
                val ayah = ayahOf(index)
                revealedIndexes += indexesOfAyah(ayah).filter { !isResolved(it) }
                publishStates()
                collectAyahNotifications(listOf(ayah))
            }
        runNotifications(notifications)
    }

    // Revealing words never passes through the microphone loop, which is what loads the next page,
    // so once the reveal buttons have shown every loaded word they would stop working. Loading
    // only then keeps a revision run, which jumps to the next due ayah, from finding it loaded.
    private fun runNotifications(notifications: List<suspend () -> Unit>) {
        scope.launch {
            notifications.forEach { notification -> notification() }
            if (synchronized(lock) { firstOpenIndex() == null }) extendWhenNearTheEnd()
        }
    }

    /**
     * Sends [grade] for the ayah waiting in [gradePrompt] to the revision schedule and clears the
     * prompt. Returns the ayah that was graded, or null when none was waiting.
     */
    suspend fun grade(grade: AyahGrade): AyahRef? {
        val prompt =
            synchronized(lock) { mutableGradePrompt.value.also { mutableGradePrompt.value = null } }
        if (prompt == null) return null
        gradeSink(prompt.ayah, grade)
        return prompt.ayah
    }

    /** Must be called with [lock] held. */
    private fun firstOpenIndex(): Int? = trackerStates.indices.firstOrNull { !isResolved(it) }

    /** Must be called with [lock] held. */
    private fun ayahOf(index: Int) = AyahRef(wordKeys[index].surah, wordKeys[index].ayah)

    /** Must be called with [lock] held. */
    private fun indexesOfAyah(ayah: AyahRef): List<Int> =
        wordKeys.indices.filter {
            wordKeys[it].surah == ayah.surah && wordKeys[it].ayah == ayah.ayah
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
                val events = withoutListenedWords(activeTracker.onPhonemes(phonemes))
                events.forEach(::apply)
                publishStates()
                collectEventNotifications(events)
            }
        notifications.forEach { notification -> notification() }
        extendWhenNearTheEnd()
    }

    // Reading the next ayahs happens off the lock, because it can touch the database.
    private suspend fun extendWhenNearTheEnd() {
        val lastLoaded = synchronized(lock) { if (isNearTheEnd()) wordKeys.lastOrNull() else null }
        if (lastLoaded == null) return
        val following = nextAyahs(AyahRef(lastLoaded.surah, lastLoaded.ayah))
        val loaded =
            try {
                readAyahs(following)
            } catch (problem: IllegalStateException) {
                lastFailure = problem
                null
            }
        synchronized(lock) {
            // A reveal button can ask for more words while the microphone loop does too, and a new
            // load can replace the words meanwhile: only the call that still sees the same last
            // word may append.
            if (wordKeys.lastOrNull() != lastLoaded) return
            if (loaded == null || loaded.first.isEmpty()) {
                nothingToAppend = true
            } else {
                appendLoaded(loaded.first, loaded.second)
            }
        }
    }

    /** Must be called with [lock] held. */
    private fun isNearTheEnd(): Boolean {
        if (nothingToAppend || tracker == null) return false
        val firstOpenWord = trackerStates.indices.firstOrNull { !isResolved(it) }
        val wordsLeft = if (firstOpenWord == null) 0 else trackerStates.size - firstOpenWord
        return wordsLeft < EXTEND_BELOW_WORDS
    }

    /** Must be called with [lock] held. */
    private fun appendLoaded(referenceWords: List<ReferenceWord>, displayWords: List<Word>) {
        tracker?.append(referenceWords)
        wordKeys =
            wordKeys +
                referenceWords.map {
                    WordKey(it.key.surah, it.key.ayah, it.key.wordPosition)
                }
        wordTextByKey = wordTextByKey + displayWords.associate { it.key to it.textUthmani }
        val oldStates = trackerStates
        trackerStates = Array(wordKeys.size) { oldStates.getOrElse(it) { WordState.Neutral } }
        mutableWords.value = mutableWords.value + displayWords
        publishStates()
    }

    /**
     * Drops what the tracker reports about words the user only listened to. Those are the words
     * before the point where reciting went on, and, when the user's first confirmed word after it
     * lies in a later ayah, the words of the ayah reciting was expected to go on with: the user may
     * start with the ayah after it. The tracker reports the skipped words in the same batch as that
     * first confirmed word. Must be called with [lock] held.
     */
    private fun withoutListenedWords(events: List<TrackerEvent>): List<TrackerEvent> {
        val pickUpIndex = continuationIndex ?: return events
        val expectedAyah = continuationExpectedAyah
        if (awaitingFirstConfirmedWord && expectedAyah != null) {
            val firstConfirmed =
                events.filterIsInstance<TrackerEvent.WordChanged>().firstOrNull {
                    it.state == WordState.Confirmed
                }
            if (firstConfirmed != null) {
                awaitingFirstConfirmedWord = false
                userSkippedContinuationAyah = ayahOf(firstConfirmed.index) > expectedAyah
            }
        }
        return events.filter { event ->
            val index = wordIndexOfFinding(event)
            if (index == null || !isListenedWord(index, pickUpIndex, expectedAyah)) {
                true
            } else {
                listenedIndexes += index
                false
            }
        }
    }

    // Must be called with [lock] held.
    private fun isListenedWord(index: Int, pickUpIndex: Int, expectedAyah: AyahRef?): Boolean =
        index < pickUpIndex || (userSkippedContinuationAyah && ayahOf(index) == expectedAyah)

    // The word a finding is about, or null for events that do not flag a word. A confirmed word is
    // never a finding.
    private fun wordIndexOfFinding(event: TrackerEvent): Int? =
        when (event) {
            is TrackerEvent.WordChanged -> event.index.takeIf { event.state != WordState.Confirmed }
            is TrackerEvent.Skipped -> event.index
            is TrackerEvent.Duration -> event.check.wordIndex
            is TrackerEvent.Substitution -> event.wordIndex
            is TrackerEvent.Inserted,
            is TrackerEvent.Anchored -> null
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
        val problems =
            ReviewRules.problemsByWord(
                wordKeys,
                trackerStates.toList(),
                skippedIndexes,
                substitutionFindings,
                durationFindings,
            )
        val states = LinkedHashMap<WordKey, WordState>()
        wordKeys.forEachIndexed { index, key ->
            val isShown = index in revealedIndexes || index in listenedIndexes
            val drawnState =
                if (problems[key] == WordProblem.Mispronounced) {
                    WordState.Mispronounced
                } else {
                    trackerStates[index]
                }
            states[key] = HifzRules.displayState(drawnState, hifz, isShown)
        }
        mutableWordStates.value = states
        val current = firstOpenIndex()?.let { wordKeys[it] }
        mutableCurrentWord.value = current
        val hintedKey = mutableHint.value?.key
        if (hintedKey != null && hintedKey != current) mutableHint.value = null
        mutableSkippedWords.value = skippedIndexes.map { wordKeys[it] }.toSet()
        mutableWordProblems.value = problems
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
            index in skippedIndexes ||
            index in revealedIndexes ||
            index in listenedIndexes

    /**
     * Finds the ayahs that just became complete. An ayah is green when every word is confirmed. In
     * hifz mode it also counts as recited once every word is confirmed or skipped, and then the
     * skips are reported. Must be called with [lock] held; the returned calls run outside it.
     */
    private fun collectEventNotifications(events: List<TrackerEvent>): List<suspend () -> Unit> =
        collectAyahNotifications(events.map { ayahOfIndex(it) }.distinct())

    // Must be called with [lock] held; [touchedAyahs] may just have finished.
    private fun collectAyahNotifications(touchedAyahs: List<AyahRef>): List<suspend () -> Unit> {
        val notifications = mutableListOf<suspend () -> Unit>()
        for (ayah in touchedAyahs) {
            val indexes = indexesOfAyah(ayah)
            val allConfirmed = indexes.all { trackerStates[it] == WordState.Confirmed }
            if (allConfirmed && greenAyahsReported.add(ayah)) {
                notifications += { progressEvents.ayahTrackedGreen(ayah) }
            }
            val allResolved = indexes.all(::isResolved)
            if (mutableHifz.value && allResolved && hifzAyahsReported.add(ayah)) {
                notifications += finishHifzAyah(ayah, indexes)
            }
        }
        return notifications
    }

    // A revealed word means the user needed help, so the recitation alone cannot grade the ayah:
    // the user is asked, and nothing is sent to the schedule until they answer.
    private fun finishHifzAyah(ayah: AyahRef, indexes: List<Int>): List<suspend () -> Unit> {
        if (indexes.any { it in revealedIndexes }) {
            mutableGradePrompt.value = GradePrompt(ayah, suggested = null)
            return emptyList()
        }
        val skipCount = indexes.count {
            trackerStates[it] == WordState.Wrong || it in skippedIndexes
        }
        mutableGradePrompt.value = GradePrompt(ayah, HifzRules.suggestedGrade(skipCount))
        return listOf { progressEvents.hifzCompleted(ayah, skipCount) }
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
        mutableWordProblems.value = emptyMap()
        mutableSkippedWords.value = emptySet()
    }

    companion object {
        const val DEFAULT_SPEED = 0.5f

        /** More words are loaded when fewer than this many are left to recite. */
        const val EXTEND_BELOW_WORDS = 15

        /** One thread that does nothing but wait for audio and run the tracker. */
        fun newRecognitionDispatcher(): CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "awn-recite").apply { isDaemon = true }
                }
                .asCoroutineDispatcher()
    }
}
