package app.awn.quran.tracker

/**
 * Turns a verdict on one word into its mark and its events: a said word is Confirmed with its
 * substituted sounds and held vowels reported; a word the cursor jumped over is said after all,
 * said wrong, or left out.
 */
internal class WordVerdicts(
    private val tables: ReferenceTables,
    private val candidates: WordCandidates,
    private val judge: WordJudge,
    private val marks: WordMarks,
    private val durations: MaddDurations,
) {
    private val reportedSubstitutions = mutableSetOf<Pair<Int, Int>>()

    fun clear() {
        reportedSubstitutions.clear()
    }

    /** Marks the word of [outcome] as said; [heardIds] are the phonemes the outcome indexes. */
    fun confirm(outcome: WordOutcome, heardIds: IntArray, events: MutableList<TrackerEvent>) {
        marks.confirm(outcome.word, events)
        reportSubstitutions(outcome, heardIds, events)
        durations.wordSaid(outcome, events)
    }

    /** Marks a word of an ayah the user left out whole. */
    fun markLeftOut(word: Int, events: MutableList<TrackerEvent>) {
        marks.markWrong(word, leftOut = true, events)
    }

    /**
     * Judges a word the cursor jumped over from the audio [before] the word it jumped to. A word
     * that already has a mark keeps it.
     */
    fun judgeJumpedWord(word: Int, before: IntArray, events: MutableList<TrackerEvent>) {
        if (marks.states[word] == WordState.Confirmed || marks.states[word] == WordState.Wrong)
            return
        val outcome = candidates.fit(before, word, word)?.outcomeOf(word)
        val attempted =
            outcome != null &&
                outcome.matches >= ATTEMPT_MATCHES &&
                outcome.matches >= ATTEMPT_SHARE * outcome.length
        when {
            outcome != null && judge.verdict(outcome) != WordVerdict.Fail ->
                confirm(outcome, before, events)
            attempted -> {
                marks.markWrong(word, leftOut = false, events)
                reportSubstitutions(checkNotNull(outcome), before, events)
            }
            else -> marks.markWrong(word, leftOut = true, events)
        }
    }

    // One event per substituted phoneme, once each. A single short-vowel swap in a long word is
    // left out: the model swaps short vowels often, and it would make a clean word yellow.
    private fun reportSubstitutions(
        outcome: WordOutcome,
        heardIds: IntArray,
        events: MutableList<TrackerEvent>,
    ) {
        val word = outcome.word
        val positions =
            (0 until outcome.length).filter {
                outcome.alignedHeard[it] >= 0 && !outcome.matched[it]
            }
        val heardSymbols = positions.map { tables.symbolOf(heardIds[outcome.alignedHeard[it]]) }
        val expectedSymbols = positions.map {
            tables.symbolOf(tables.expectedIds[tables.wordStartOffsets[word] + it])
        }
        val isLoneVowelSwap =
            positions.size == 1 &&
                outcome.length >= AlignmentThresholds.LONG_WORD_MINIMUM_LENGTH &&
                WordScore.isShortVowelSwap(heardSymbols[0], expectedSymbols[0])
        if (isLoneVowelSwap) return
        for ((index, position) in positions.withIndex()) {
            val phonemeIndex = tables.originalPosition(word, position)
            if (!reportedSubstitutions.add(word to phonemeIndex)) continue
            events +=
                TrackerEvent.Substitution(
                    wordIndex = word,
                    phonemeIndex = phonemeIndex,
                    heard = heardSymbols[index],
                    expected = tables.words[word].phonemes[phonemeIndex],
                )
        }
    }

    private companion object {
        // A jumped word with this many matched phonemes, and this share of its length, was said
        // wrong rather than left out.
        const val ATTEMPT_MATCHES = 2
        const val ATTEMPT_SHARE = 0.3
    }
}
