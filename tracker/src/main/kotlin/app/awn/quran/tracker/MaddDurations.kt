package app.awn.quran.tracker

/**
 * Measures the lengthened vowels of said words. A vowel lasts from its token's onset to the onset
 * of the next token that matched one of the next [MADD_END_LOOKAHEAD_PHONEMES] expected phonemes
 * (the model sometimes puts an extra token inside a held vowel), else to the next heard token. A
 * word is measured once the word after it is said, because its last vowel ends in that word.
 */
internal class MaddDurations(private val tables: ReferenceTables, private val heard: HeardBuffer) {

    private val holdTimeEstimator = HoldTimeEstimator()
    private val reported = mutableSetOf<Pair<Int, Int>>()

    // For every said word, the absolute heard index of each merged phoneme heard as itself, or -1.
    private val matchedHeardByWord = mutableMapOf<Int, IntArray>()

    fun observe(token: PhonemeEvent) = holdTimeEstimator.observe(token)

    fun clear() {
        holdTimeEstimator.clear()
        reported.clear()
        matchedHeardByWord.clear()
    }

    /**
     * Remembers where the phonemes of a word just said were heard, and measures the word before.
     */
    fun wordSaid(outcome: WordOutcome, events: MutableList<TrackerEvent>) {
        matchedHeardByWord[outcome.word] =
            IntArray(outcome.length) { position ->
                if (outcome.matched[position]) {
                    heard.absoluteIndex(outcome.alignedHeard[position])
                } else {
                    -1
                }
            }
        if (outcome.word > 0 && matchedHeardByWord.containsKey(outcome.word - 1)) {
            report(outcome.word - 1, events)
        }
    }

    private fun report(word: Int, events: MutableList<TrackerEvent>) {
        val harakaSeconds = holdTimeEstimator.harakaSeconds() ?: return
        for (madd in tables.words[word].madds) {
            measure(word, madd, harakaSeconds)?.let { events += TrackerEvent.Duration(it) }
        }
    }

    // Null when the vowel was not heard as itself, nothing was heard after it yet, or it was
    // measured before.
    private fun measure(word: Int, madd: MaddAnnotation, harakaSeconds: Float): DurationCheck? {
        val position = tables.mergedPosition(word, madd.phonemeIndex)
        val vowelIndex = matchedHeardByWord.getValue(word).getOrElse(position) { -1 }
        val vowelOnset = if (vowelIndex >= 0) heard.onsetAt(vowelIndex) else null
        val followingOnset = heard.onsetAt(vowelIndex + 1)
        val isMeasurable = vowelOnset != null && followingOnset != null
        if (!isMeasurable || !reported.add(word to madd.phonemeIndex)) return null
        val start = checkNotNull(vowelOnset)
        val endOnset = nextMatchedOnset(word, position, start) ?: checkNotNull(followingOnset)
        val measured = endOnset - start
        val expected = madd.harakat * harakaSeconds
        val verdict = judgeDuration(measured, expected, harakaSeconds)
        return DurationCheck(word, madd.letter, madd.rule, measured, expected, verdict, start)
    }

    // The onset of the first of the next expected phonemes (in this word or the next) that was
    // heard as itself, later than the vowel; null when none of them was.
    private fun nextMatchedOnset(word: Int, position: Int, vowelOnset: Float): Float? {
        val vowelOffset = tables.wordStartOffsets[word] + position
        return (1..MADD_END_LOOKAHEAD_PHONEMES)
            .map { vowelOffset + it }
            .filter { it < tables.expectedIds.size }
            .mapNotNull { offset ->
                val owner = tables.wordAtOffset(offset)
                val index =
                    matchedHeardByWord[owner]?.get(offset - tables.wordStartOffsets[owner]) ?: -1
                if (index >= 0) heard.onsetAt(index) else null
            }
            .firstOrNull { it > vowelOnset + MINIMUM_ONSET_STEP_SECONDS }
    }

    // A deviation of more than one haraka is a deviation; anything closer is fine. A vowel is
    // called too short only when it is also under UNDERHELD_MAXIMUM_SHARE of the expected time:
    // Husary's clean recitation measures at about half the time the rules ask for on long madds,
    // and flagging those made one word in nine look mispronounced.
    private fun judgeDuration(
        measured: Float,
        expected: Float,
        harakaSeconds: Float,
    ): DurationVerdict =
        when {
            measured < expected - harakaSeconds && measured < expected * UNDERHELD_MAXIMUM_SHARE ->
                DurationVerdict.Underheld
            measured > expected + harakaSeconds -> DurationVerdict.Overheld
            else -> DurationVerdict.Ok
        }

    private companion object {
        // A held vowel ends at the first matched token among this many expected phonemes after it.
        const val MADD_END_LOOKAHEAD_PHONEMES = 2

        // A vowel shorter than this share of the expected time (and more than a haraka short) is
        // too short.
        const val UNDERHELD_MAXIMUM_SHARE = 0.4f

        // Onsets lie on a 0.04 s grid; a later onset by less than this is the same token.
        const val MINIMUM_ONSET_STEP_SECONDS = 0.001f
    }
}
