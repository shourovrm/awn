package app.awn.quran.tracker

/** What one expected word got in a fit: which of its phonemes were heard, and how. */
internal class WordOutcome(val word: Int, length: Int) {
    var matches = 0
    var extra = 0
    val substitutionCosts = mutableListOf<Double>()

    /**
     * For each merged phoneme, the heard index it was aligned to (match or substitution), or -1.
     */
    val alignedHeard = IntArray(length) { -1 }

    /** For each merged phoneme, true when it was heard as itself. */
    val matched = BooleanArray(length)

    val length: Int
        get() = alignedHeard.size

    val aligned: Int
        get() = alignedHeard.count { it >= 0 }

    /** Phonemes heard as themselves or as a near sound (a substitution cheaper than unrelated). */
    val heardClosely: Int
        get() = matches + substitutionCosts.count { it < 1.0 }

    /** True when the first two phonemes were heard as themselves. */
    val startsCleanly: Boolean
        get() = length >= 2 && matched[0] && matched[1]

    val missing: Int
        get() = length - aligned

    /** The first heard index aligned to this word, or -1 when none was. */
    val firstHeard: Int
        get() = alignedHeard.firstOrNull { it >= 0 } ?: -1

    /** One past the last heard index aligned to this word, or -1 when none was. */
    val endHeard: Int
        get() = alignedHeard.lastOrNull { it >= 0 }?.plus(1) ?: -1
}

/**
 * The best fit of the words [firstWord] to [lastWord] inside the heard phonemes, word by word.
 * [heardStart] is the first heard phoneme it used; -1 when it used none.
 */
internal class CandidateFit(
    val firstWord: Int,
    val lastWord: Int,
    val outcomes: List<WordOutcome>,
) {
    fun outcomeOf(word: Int): WordOutcome = outcomes[word - firstWord]

    val heardStart: Int
        get() = outcomes.map { it.firstHeard }.filter { it >= 0 }.minOrNull() ?: -1
}

/** Fits loaded words inside the heard phonemes; see [WordFitter]. */
internal class WordCandidates(private val tables: ReferenceTables) {

    /** Fits [firstWord] to [lastWord] (both included) inside [heard]; null when out of range. */
    fun fit(heard: IntArray, firstWord: Int, lastWord: Int): CandidateFit? {
        if (firstWord < 0 || lastWord >= tables.size || firstWord > lastWord) return null
        val expected = tables.idsOfWords(firstWord, lastWord)
        val operations = WordFitter.fit(heard, expected)
        val outcomes = (firstWord..lastWord).map { WordOutcome(it, tables.wordLengths[it]) }
        collect(operations, heard, expected, firstWord, outcomes)
        return CandidateFit(firstWord, lastWord, outcomes)
    }

    private fun collect(
        operations: List<AlignmentOperation>,
        heard: IntArray,
        expected: IntArray,
        firstWord: Int,
        outcomes: List<WordOutcome>,
    ) {
        val firstOffset = tables.wordStartOffsets[firstWord]
        var wordOfLastAligned = -1
        var insertionsSinceLastAligned = 0
        for (operation in operations) {
            when (operation.step) {
                AlignmentStep.Insertion -> insertionsSinceLastAligned++
                AlignmentStep.Deletion -> Unit
                AlignmentStep.Match,
                AlignmentStep.Substitution -> {
                    val offset = firstOffset + operation.expectedIndex
                    val word = tables.wordAtOffset(offset)
                    val outcome = outcomes[word - firstWord]
                    // Heard sounds between two aligned sounds of one word are extra sounds in it.
                    if (word == wordOfLastAligned) outcome.extra += insertionsSinceLastAligned
                    insertionsSinceLastAligned = 0
                    wordOfLastAligned = word
                    val position = offset - tables.wordStartOffsets[word]
                    outcome.alignedHeard[position] = operation.heardIndex
                    if (operation.step == AlignmentStep.Match) {
                        outcome.matches++
                        outcome.matched[position] = true
                    } else {
                        outcome.substitutionCosts +=
                            WordScore.substitutionCost(
                                heard = tables.symbolOf(heard[operation.heardIndex]),
                                expected = tables.symbolOf(expected[operation.expectedIndex]),
                            )
                    }
                }
            }
        }
    }
}
