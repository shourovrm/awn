package app.awn.quran.tracker

/** A tracker event with the moment of the recording at which it was reported. */
data class TimedEvent(val seconds: Float, val event: TrackerEvent)

/** What the tracker did with one recording. */
class TrackingResult(
    val fixture: Fixture,
    val surah: SurahReference,
    val events: List<TimedEvent>,
    val finalStates: List<WordState>,
    val timeline: List<TruthSegment>,
) {
    private val cutRange: IntRange? = fixture.cutAyah?.let { surah.wordIndices(it) }

    val recitedWordIndices: List<Int> =
        surah.words.indices.filter { index -> surah.ayahOfWord(index) != fixture.cutAyah }

    val greenWordCount: Int
        get() = recitedWordIndices.count { finalStates[it] == WordState.Confirmed }

    val skippedWordIndices: List<Int>
        get() = events.map { it.event }.filterIsInstance<TrackerEvent.Skipped>().map { it.index }

    /** True when a word of the cut ayah was reported as skipped. */
    val cutAyahFlagged: Boolean
        get() = cutRange != null && skippedWordIndices.any { it in cutRange }

    /** Skips reported for words the user did recite. */
    val falseSkipCount: Int
        get() = skippedWordIndices.count { cutRange == null || it !in cutRange }

    val substitutions: List<TrackerEvent.Substitution>
        get() = events.map { it.event }.filterIsInstance<TrackerEvent.Substitution>()

    val durations: List<DurationCheck>
        get() = events.map { it.event }.filterIsInstance<TrackerEvent.Duration>().map { it.check }

    /** Confirmed words with a substituted sound: the session shows them as mispronounced. */
    val mispronouncedBySoundIndices: Set<Int>
        get() =
            substitutions
                .map { it.wordIndex }
                .filter { finalStates[it] == WordState.Confirmed }
                .toSet()

    /** Confirmed words with a vowel held too short or too long. */
    val mispronouncedByDurationIndices: Set<Int>
        get() =
            durations
                .filter { it.verdict != DurationVerdict.Ok }
                .map { it.wordIndex }
                .filter { finalStates[it] == WordState.Confirmed }
                .toSet()

    val insertions: List<TimedEvent>
        get() = events.filter { it.event is TrackerEvent.Inserted }

    val anchors: List<TimedEvent>
        get() = events.filter { it.event is TrackerEvent.Anchored }

    /** Anchors that point at an ayah other than the one heard around the moment of the anchor. */
    val wrongAyahAnchorCount: Int
        get() = anchors.count { timed -> !anchorMatchesTruth(timed) }

    private fun anchorMatchesTruth(timed: TimedEvent): Boolean {
        val anchored = timed.event as TrackerEvent.Anchored
        val targetAyah = surah.ayahOfWord(anchored.index)
        val windowStart = timed.seconds - ANCHOR_LOOK_BACK_SECONDS
        return timeline.any {
            it.ayah == targetAyah &&
                it.endSeconds >= windowStart &&
                it.startSeconds <= timed.seconds
        }
    }

    private companion object {
        // The model reports a phoneme up to about a second after it was spoken.
        const val ANCHOR_LOOK_BACK_SECONDS = 3f
    }
}

object TrackingEvaluation {
    private const val BLOCK_SECONDS = 0.1f

    fun replay(
        fixture: Fixture,
        recording: PhonemeRecording,
        surah: SurahReference,
        config: TrackerConfig = TrackerConfig(),
    ): TrackingResult {
        val tracker = AlignmentTracker(config)
        tracker.load(surah.words, startIndex = 0)
        val events = mutableListOf<TimedEvent>()
        for ((blockIndex, blockEvents) in recording.eventsPerBlock.withIndex()) {
            val seconds = (blockIndex + 1) * BLOCK_SECONDS
            for (event in tracker.onPhonemes(blockEvents)) events += TimedEvent(seconds, event)
        }
        val finalStates = surah.words.indices.map { tracker.stateOfWord(it) }
        val timeline = TruthTimeline.of(fixture, surah)
        return TrackingResult(fixture, surah, events, finalStates, timeline)
    }

    /**
     * True when the spliced word was flagged: an insertion reported next to the splice point, or a
     * word beside the splice point that is red here but not in the unmodified recitation.
     */
    fun spliceFlagged(result: TrackingResult, unmodified: TrackingResult): Boolean {
        val afterAyah = checkNotNull(result.fixture.spliceAfterAyah)
        val lastWordBefore = result.surah.wordIndices(afterAyah).last
        val nextAyahWords =
            if (afterAyah < result.surah.ayahCount) {
                result.surah.wordIndices(afterAyah + 1).first
            } else {
                lastWordBefore
            }
        val insertedNearby =
            result.insertions.any {
                val afterIndex = (it.event as TrackerEvent.Inserted).afterIndex
                afterIndex in (lastWordBefore - 1)..(lastWordBefore + 1)
            }
        val redNearby =
            listOf(lastWordBefore, nextAyahWords).any {
                result.finalStates[it] == WordState.Wrong &&
                    unmodified.finalStates[it] != WordState.Wrong
            }
        return insertedNearby || redNearby
    }
}
