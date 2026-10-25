package app.awn.quran.tracker.fakes

import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.Tracker
import app.awn.quran.tracker.TrackerConfig
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.TrackerFactory
import app.awn.quran.tracker.WordState

/**
 * A tracker that does no alignment. Each call to [onPhonemes] returns the next batch from
 * [scriptedBatches] (empty when the script has run out) and applies [TrackerEvent.WordChanged]
 * events to the word states, so a test controls exactly what the screen under test sees.
 */
class FakeTracker(initialConfig: TrackerConfig = TrackerConfig()) : Tracker {

    val scriptedBatches: ArrayDeque<List<TrackerEvent>> = ArrayDeque()
    var config: TrackerConfig = initialConfig
        private set

    private var reference: List<ReferenceWord> = emptyList()
    private var startIndex = 0
    private val states = mutableMapOf<Int, WordState>()

    override fun load(reference: List<ReferenceWord>, startIndex: Int) {
        this.reference = reference
        this.startIndex = startIndex
        states.clear()
    }

    /** Every list given to [append], in order. */
    val appended = mutableListOf<List<ReferenceWord>>()

    override fun append(words: List<ReferenceWord>) {
        appended += words
        reference = reference + words
    }

    // Set by a scripted Repeated event: the word after the repeated one, until the user passes it.
    private var repeatCursor: Int? = null

    override fun onPhonemes(events: List<PhonemeEvent>): List<TrackerEvent> {
        val batch = scriptedBatches.removeFirstOrNull().orEmpty()
        for (event in batch) {
            when (event) {
                is TrackerEvent.WordChanged -> {
                    states[event.index] = event.state
                    if (event.index == repeatCursor) repeatCursor = event.index + 1
                }
                is TrackerEvent.Repeated -> repeatCursor = event.firstWord + 1
                else -> Unit
            }
        }
        return batch
    }

    override fun stateOfWord(index: Int): WordState = states[index] ?: WordState.Neutral

    /**
     * The first word not passed (confirmed, red or unread), or the word after a scripted repeat.
     */
    override fun currentIndex(): Int {
        var index = startIndex
        while (index < reference.size && states[index].isPassed()) index++
        val repeating = repeatCursor
        return if (repeating != null && repeating < index) repeating else index
    }

    private fun WordState?.isPassed() =
        this == WordState.Confirmed || this == WordState.Wrong || this == WordState.Unread

    override fun setConfig(config: TrackerConfig) {
        this.config = config
    }

    override fun reset() {
        states.clear()
        repeatCursor = null
    }

    /** Every index given to [continueFrom], in order. */
    val continuedFrom = mutableListOf<Int>()

    override fun continueFrom(index: Int) {
        continuedFrom += index
        startIndex = index
        states.keys.removeAll { it >= index }
    }
}

/** Creates [FakeTracker]s and keeps them in [created] so a test can script the one in use. */
class FakeTrackerFactory : TrackerFactory {
    val created = mutableListOf<FakeTracker>()

    override fun create(config: TrackerConfig): Tracker = FakeTracker(config).also { created += it }
}

/** Serves reference words from a map keyed by surah and ayah. */
class FakeReferencePhonemes(
    val words: MutableMap<Pair<Int, Int>, List<ReferenceWord>> = mutableMapOf(),
) : ReferencePhonemes {

    override fun forAyah(surah: Int, ayah: Int): List<ReferenceWord> =
        words[surah to ayah].orEmpty()
}
