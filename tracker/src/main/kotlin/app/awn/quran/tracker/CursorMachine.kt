package app.awn.quran.tracker

/**
 * Moves the cursor (the word the user is expected to say next) through the loaded words as heard
 * phonemes arrive, and marks the words. Each update runs [CursorDecisions] until no step applies;
 * audio a word took is gone, so old audio is never re-routed to other words. When every candidate
 * keeps failing, [LostSearch] may move the cursor anywhere in the loaded words.
 */
internal class CursorMachine(private val tables: ReferenceTables) {

    val marks = WordMarks()
    val heard = HeardBuffer()
    val durations = MaddDurations(tables, heard)
    private val candidates = WordCandidates(tables)
    private val judge = WordJudge(tables)
    private val decisions = CursorDecisions(tables, candidates, judge)
    private val verdicts = WordVerdicts(tables, candidates, judge, marks, durations)

    var thresholds: AlignmentThresholds
        get() = judge.thresholds
        set(value) {
            judge.thresholds = value
        }

    var cursor = 0
        private set

    // The first word of this microphone run, which counts as bounded on its left.
    private var runStart = 0

    // The fit of the cursor word while it waits for its last consonant.
    private var heldFit: CandidateFit? = null

    /** Forgets the audio and starts following at [index]. */
    fun restartAt(index: Int) {
        cursor = index
        runStart = index
        heard.clear()
        durations.clear()
        verdicts.clear()
        heldFit = null
    }

    /** A pause longer than a breath before the token at [nextOnset] closes a held cursor word. */
    fun closeHeldWordAfterPause(nextOnset: Float, events: MutableList<TrackerEvent>) {
        val fit = heldFit ?: return
        val lastOnset = heard.lastOnset() ?: return
        if (nextOnset - lastOnset > BREATH_SECONDS)
            say(fit, cursor..cursor, heard.toIntArray(), events)
    }

    fun advance(events: MutableList<TrackerEvent>) {
        advanceWords(events)
        if (searchWhenLost(events)) advanceWords(events)
        if (heard.size > MAXIMUM_HEARD_PHONEMES) heard.drop(heard.size - MAXIMUM_HEARD_PHONEMES)
    }

    private fun advanceWords(events: MutableList<TrackerEvent>) {
        var stepCount = 0
        while (stepCount < MAXIMUM_STEPS_PER_UPDATE && cursor < tables.size) {
            val heardIds = heard.toIntArray()
            val context =
                CursorContext(heardIds, heard.carriedCount, cursor, marks.states, runStart)
            val step = decisions.decide(context)
            if (!apply(step, heardIds, events)) {
                markCursorWhileHeard(step, heardIds, events)
                return
            }
            stepCount++
        }
    }

    // Returns false when the step does not move the cursor.
    private fun apply(
        step: CursorStep,
        heardIds: IntArray,
        events: MutableList<TrackerEvent>,
    ): Boolean {
        when (step) {
            is CursorStep.Say -> say(step.fit, step.words, heardIds, events)
            is CursorStep.Skip -> skip(step, heardIds, events)
            is CursorStep.Repeat -> repeat(step, heardIds, events)
            is CursorStep.Hold -> heldFit = step.fit
            CursorStep.Wait -> heldFit = null
        }
        return step is CursorStep.Say || step is CursorStep.Skip || step is CursorStep.Repeat
    }

    // The word being said, or held, has no fill; it shows only that the user is on it.
    private fun markCursorWhileHeard(
        step: CursorStep,
        heardIds: IntArray,
        events: MutableList<TrackerEvent>,
    ) {
        val outcome = candidates.fit(heardIds, cursor, cursor)?.outcomeOf(cursor) ?: return
        if (step is CursorStep.Hold || outcome.matches >= TENTATIVE_MATCHES) {
            marks.markTentative(cursor, events)
        }
    }

    private fun say(
        fit: CandidateFit,
        words: IntRange,
        heardIds: IntArray,
        events: MutableList<TrackerEvent>,
    ) {
        val unexplained = fit.heardStart - heard.carriedCount
        if (unexplained >= thresholds.insertionPhonemes) {
            events += TrackerEvent.Inserted(maxOf(words.first - 1, 0))
        }
        for (word in words) verdicts.confirm(fit.outcomeOf(word), heardIds, events)
        heard.consumeUntil(fit.outcomeOf(words.last).endHeard)
        cursor = words.last + 1
        heldFit = null
    }

    private fun skip(step: CursorStep.Skip, heardIds: IntArray, events: MutableList<TrackerEvent>) {
        // The carried phoneme belongs to the word before; it is no attempt at a jumped word.
        val before = heardIds.copyOfRange(0, step.fit.heardStart.coerceAtLeast(0))
        for (index in 0 until minOf(heard.carriedCount, before.size)) before[index] = NO_SYMBOL
        for (word in step.jumped) {
            when {
                !step.reassigned.isEmpty() -> marks.markLeftOutAfterAll(word, events)
                step.isLeftOutAyah -> verdicts.markLeftOut(word, events)
                else -> verdicts.judgeJumpedWord(word, before, events)
            }
        }
        for (word in step.reassigned) marks.confirm(word, events)
        for (word in step.words) verdicts.confirm(step.fit.outcomeOf(word), heardIds, events)
        heard.consumeUntil(step.fit.outcomeOf(step.words.last).endHeard)
        cursor = step.words.last + 1
        heldFit = null
    }

    private fun repeat(
        step: CursorStep.Repeat,
        heardIds: IntArray,
        events: MutableList<TrackerEvent>,
    ) {
        val outcome = step.fit.outcomeOf(step.word)
        events += TrackerEvent.Repeated(step.word, cursor - 1)
        verdicts.confirm(outcome, heardIds, events)
        heard.consumeUntil(outcome.endHeard)
        cursor = step.word + 1
        heldFit = null
    }

    // A forward jump leaves the words in between Unread; a backward one keeps every mark.
    // Each early return is a condition of the search; see LostSearch.
    @Suppress("ReturnCount")
    private fun searchWhenLost(events: MutableList<TrackerEvent>): Boolean {
        if (heard.unexplainedCount < thresholds.lostPhonemes) return false
        val heardIds = heard.toIntArray()
        val queryStart = (heardIds.size - thresholds.lostQueryPhonemes).coerceAtLeast(0)
        val query = heardIds.copyOfRange(queryStart, heardIds.size)
        val place = LostSearch.find(query, tables, thresholds) ?: return false
        if (place.word in cursor..cursor + 2) return false
        if (place.word > cursor) {
            for (word in cursor until place.word) marks.markUnread(word, events)
            events += TrackerEvent.JumpedAhead(cursor, place.word)
        }
        events += TrackerEvent.Anchored(place.word)
        // The match may start inside the word; the audio of the word's start stays with it.
        heard.drop(queryStart + place.heardStart - place.phonemesIntoWord - FRONT_KEEP_PHONEMES)
        cursor = place.word
        heldFit = null
        return true
    }

    private companion object {
        const val NO_SYMBOL = -1
        const val BREATH_SECONDS = 0.6f
        const val MAXIMUM_STEPS_PER_UPDATE = 64

        // Unexplained audio beyond this is too old to belong to any word still to come.
        const val MAXIMUM_HEARD_PHONEMES = 64

        // A stray sound or two before the word a jump lands on stays with it.
        const val FRONT_KEEP_PHONEMES = 2

        // The cursor word shows as being said once this many of its phonemes are heard.
        const val TENTATIVE_MATCHES = 2
    }
}
