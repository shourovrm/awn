package app.awn.quran.tracker

/**
 * Follows a recitation by aligning the heard phonemes to the expected ones.
 *
 * Every heard character of a model token is kept, except that a run of one repeated character is
 * merged into one: the model card says madd lengths are unreliable, and a long vowel is written as
 * a run of one character. The expected words are merged the same way, word by word.
 *
 * Each time phonemes arrive, the heard sequence since the last settled word is aligned
 * ([PhonemeAligner]) to the expected phonemes from the first unsettled word to the end of the ayah
 * two after the one it lies in. The alignment decides, for every expected word, how many of its
 * phonemes were matched, substituted or left out:
 * - green ([WordState.Confirmed]) when the aligned share reaches the green coverage with at most
 *   one substitution; the first verdict of green is kept;
 * - red ([WordState.Wrong]) when a word the user has moved past has two or more substitutions, or
 *   when it was left out entirely (together with a [TrackerEvent.Skipped]);
 * - yellow ([WordState.Tentative]) for a partly covered word. A word is settled once the user is
 *   heard in a later word. Settled words leave the front of the heard sequence, so the table stays
 *   small however long the recitation is.
 */
// The class is one state machine over one set of tables; splitting it would only pass those
// tables around. Early returns and continues keep each step flat instead of nested.
@Suppress(
    "TooManyFunctions",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "CyclomaticComplexMethod",
)
class AlignmentTracker(config: TrackerConfig = TrackerConfig()) : Tracker {

    private var thresholds = AlignmentThresholds.forSpeed(config.speed)
    private var reference: List<ReferenceWord> = emptyList()
    private var startIndex = 0

    // Reference tables, built by load().
    private val symbolIds = mutableMapOf<String, Int>()
    private var expectedIds = IntArray(0)
    private var wordStartOffsets = IntArray(0)
    private var wordLengths = IntArray(0)
    private var ayahNumberOfWord = IntArray(0)
    private val symbolsById = mutableListOf<String>()

    // For every word, the position in ReferenceWord.phonemes of each merged phoneme.
    private var originalPositions = emptyList<IntArray>()

    // Progress, cleared by reset().
    private var states = emptyArray<WordState>()
    private var settled = BooleanArray(0)
    private var anchorIndex = 0
    private var heardIds = mutableListOf<Int>()
    private var heardOnsets = mutableListOf<Float>()
    private val holdTimeEstimator = HoldTimeEstimator()
    private val reportedDurations = mutableSetOf<Pair<Int, Int>>()
    private var previousHeardId = NO_SYMBOL
    private var droppedHeardCount = 0
    private var lastReportedInsertionEnd = -1
    private val reportedSubstitutions = mutableSetOf<Pair<Int, Int>>()

    override fun load(reference: List<ReferenceWord>, startIndex: Int) {
        this.reference = reference
        this.startIndex = startIndex
        buildReferenceTables()
        reset()
    }

    override fun reset() {
        states = Array(reference.size) { WordState.Neutral }
        settled = BooleanArray(reference.size)
        anchorIndex = startIndex
        heardIds = mutableListOf()
        heardOnsets = mutableListOf()
        holdTimeEstimator.clear()
        reportedDurations.clear()
        previousHeardId = NO_SYMBOL
        droppedHeardCount = 0
        lastReportedInsertionEnd = -1
        reportedSubstitutions.clear()
    }

    override fun setConfig(config: TrackerConfig) {
        thresholds = AlignmentThresholds.forSpeed(config.speed)
    }

    override fun stateOfWord(index: Int): WordState = states.getOrElse(index) { WordState.Neutral }

    override fun currentIndex(): Int {
        var index = anchorIndex
        while (index < states.size && states[index].isDone()) index++
        return index
    }

    override fun onPhonemes(events: List<PhonemeEvent>): List<TrackerEvent> {
        if (anchorIndex >= reference.size) return emptyList()
        val addedCount = appendHeard(events)
        if (addedCount == 0) return emptyList()
        return alignAndReport()
    }

    private fun WordState.isDone() = this == WordState.Confirmed || this == WordState.Wrong

    // ---- Reference tables ----------------------------------------------------------------

    private fun buildReferenceTables() {
        symbolIds.clear()
        symbolsById.clear()
        val positions = mutableListOf<IntArray>()
        val flattened = mutableListOf<Int>()
        wordStartOffsets = IntArray(reference.size + 1)
        wordLengths = IntArray(reference.size)
        ayahNumberOfWord = IntArray(reference.size)
        var ayahSequence = 0
        for ((wordIndex, word) in reference.withIndex()) {
            val startsNewAyah =
                wordIndex == 0 ||
                    word.key.surah != reference[wordIndex - 1].key.surah ||
                    word.key.ayah != reference[wordIndex - 1].key.ayah
            if (startsNewAyah) ayahSequence++
            ayahNumberOfWord[wordIndex] = ayahSequence
            wordStartOffsets[wordIndex] = flattened.size
            val ids = word.phonemes.map { idOf(it) }
            val merged = mergeRepeats(ids)
            positions += firstPositionOfEachRun(ids)
            flattened += merged
            wordLengths[wordIndex] = merged.size
        }
        originalPositions = positions
        wordStartOffsets[reference.size] = flattened.size
        expectedIds = flattened.toIntArray()
    }

    private fun idOf(symbol: String): Int =
        symbolIds.getOrPut(symbol) {
            symbolsById += symbol
            symbolsById.size - 1
        }

    private fun firstPositionOfEachRun(ids: List<Int>): IntArray {
        val positions = mutableListOf<Int>()
        for ((position, id) in ids.withIndex()) {
            if (position == 0 || ids[position - 1] != id) positions += position
        }
        return positions.toIntArray()
    }

    private fun mergeRepeats(ids: List<Int>): List<Int> {
        val merged = mutableListOf<Int>()
        for (id in ids) {
            if (merged.isEmpty() || merged.last() != id) merged += id
        }
        return merged
    }

    // Returns the last expected word (exclusive) the alignment may reach from [anchor].
    private fun windowEndWord(anchor: Int): Int {
        val lastAyahNumber = ayahNumberOfWord[anchor] + AYAHS_AHEAD_IN_WINDOW
        var end = anchor
        while (end < reference.size && ayahNumberOfWord[end] <= lastAyahNumber) end++
        return end
    }

    // ---- Heard phonemes ------------------------------------------------------------------

    private fun appendHeard(events: List<PhonemeEvent>): Int {
        var added = 0
        for (event in events) {
            if (event.symbol == BLANK_SYMBOL) continue
            holdTimeEstimator.observe(event)
            for (character in event.symbol) {
                val id = idOf(character.toString())
                if (id == previousHeardId) continue
                heardIds += id
                heardOnsets += event.onsetSeconds
                previousHeardId = id
                added++
            }
        }
        return added
    }

    // ---- Alignment and judgement ----------------------------------------------------------

    private fun alignAndReport(): List<TrackerEvent> {
        val events = mutableListOf<TrackerEvent>()
        val windowEnd = windowEndWord(anchorIndex)
        val expectedStart = wordStartOffsets[anchorIndex]
        val expected = expectedIds.copyOfRange(expectedStart, wordStartOffsets[windowEnd])
        val operations = PhonemeAligner.align(heardIds.toIntArray(), expected)
        val statistics = collectStatistics(operations, expectedStart, windowEnd)

        judgeWords(statistics, windowEnd, events)
        val frontier = findFrontier(statistics, windowEnd)
        if (frontier != null) {
            reportSubstitutions(operations, expectedStart, frontier, events)
            reportDurations(operations, expectedStart, frontier, events)
        }
        reportInsertion(operations, expectedStart, events)
        settleWords(operations, expectedStart)
        reanchorIfLost(operations, events)
        return events
    }

    private class WordStatistics {
        var matches = 0
        var substitutions = 0
        val aligned: Int
            get() = matches + substitutions
    }

    private fun collectStatistics(
        operations: List<AlignmentOperation>,
        expectedStart: Int,
        windowEnd: Int,
    ): Array<WordStatistics> {
        val statistics = Array(windowEnd) { WordStatistics() }
        for (operation in operations) {
            if (
                operation.step == AlignmentStep.Insertion ||
                    operation.step == AlignmentStep.Deletion
            ) {
                continue
            }
            val word = wordAtExpectedOffset(expectedStart + operation.expectedIndex)
            if (operation.step == AlignmentStep.Match) {
                statistics[word].matches++
            } else {
                statistics[word].substitutions++
            }
        }
        return statistics
    }

    private fun wordAtExpectedOffset(offset: Int): Int {
        var low = 0
        var high = reference.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (wordStartOffsets[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    private fun judgeWords(
        statistics: Array<WordStatistics>,
        windowEnd: Int,
        events: MutableList<TrackerEvent>,
    ) {
        val frontier = findFrontier(statistics, windowEnd) ?: return
        // Passed words that cannot be settled yet: left out, or too poorly matched to trust.
        val waitingWords = mutableListOf<Int>()
        for (word in anchorIndex..frontier) {
            val wordStatistics = statistics[word]
            val isPassed = word < frontier
            if (wordStatistics.aligned == 0) {
                if (isPassed) waitingWords += word
                continue
            }
            val isGood = isGreen(wordStatistics, word)
            if (isGood && waitingWords.isNotEmpty()) {
                val evidence = (word..frontier).sumOf { statistics[it].matches }
                if (evidence >= thresholds.skipEvidencePhonemes) {
                    settleWaitingWords(waitingWords, statistics, events)
                    waitingWords.clear()
                }
            }
            val verdict =
                when {
                    isGood -> WordState.Confirmed
                    isPassed && wordStatistics.substitutions >= thresholds.wrongSubstitutions ->
                        WordState.Wrong
                    else -> WordState.Tentative
                }
            changeState(word, verdict, events)
            if (!isPassed) continue
            val matchShare = wordStatistics.matches.toFloat() / wordLengths[word]
            if (waitingWords.isEmpty() && (isGood || matchShare >= MINIMUM_MATCH_SHARE_TO_SETTLE)) {
                settled[word] = true
            } else {
                waitingWords += word
            }
        }
    }

    // A waiting word with no aligned phoneme was left out; the others keep their displayed verdict.
    private fun settleWaitingWords(
        words: List<Int>,
        statistics: Array<WordStatistics>,
        events: MutableList<TrackerEvent>,
    ) {
        for (word in words) {
            if (statistics[word].aligned == 0) {
                changeState(word, WordState.Wrong, events)
                events += TrackerEvent.Skipped(word)
            }
            settled[word] = true
        }
    }

    // Substitutions are reported for words the user has moved past, once each.
    private fun reportSubstitutions(
        operations: List<AlignmentOperation>,
        expectedStart: Int,
        frontier: Int,
        events: MutableList<TrackerEvent>,
    ) {
        for (operation in operations) {
            if (operation.step != AlignmentStep.Substitution) continue
            val offset = expectedStart + operation.expectedIndex
            val word = wordAtExpectedOffset(offset)
            if (word >= frontier) continue
            val mergedPosition = offset - wordStartOffsets[word]
            val phonemeIndex = originalPositions[word][mergedPosition]
            if (!reportedSubstitutions.add(word to phonemeIndex)) continue
            events +=
                TrackerEvent.Substitution(
                    wordIndex = word,
                    phonemeIndex = phonemeIndex,
                    heard = symbolsById[heardIds[operation.heardIndex]],
                    expected = reference[word].phonemes[phonemeIndex],
                )
        }
    }

    // The word the user is saying now: the last one with enough aligned phonemes.
    private fun findFrontier(statistics: Array<WordStatistics>, windowEnd: Int): Int? =
        (anchorIndex until windowEnd).lastOrNull {
            statistics[it].aligned >= PASSED_EVIDENCE_PHONEMES
        }

    // Measures every lengthened vowel of the words the user has moved past, once each.
    private fun reportDurations(
        operations: List<AlignmentOperation>,
        expectedStart: Int,
        frontier: Int,
        events: MutableList<TrackerEvent>,
    ) {
        val harakaSeconds = holdTimeEstimator.harakaSeconds() ?: return
        val heardIndexByExpectedOffset = mutableMapOf<Int, Int>()
        for (operation in operations) {
            if (operation.step == AlignmentStep.Match) {
                val offset = expectedStart + operation.expectedIndex
                heardIndexByExpectedOffset[offset] = operation.heardIndex
            }
        }
        for (word in anchorIndex until frontier) {
            for (madd in reference[word].madds) {
                val mergedPosition = originalPositions[word].indexOf(madd.phonemeIndex)
                val offset = wordStartOffsets[word] + mergedPosition
                val heardIndex = heardIndexByExpectedOffset[offset] ?: continue
                if (heardIndex + 1 >= heardOnsets.size) continue
                if (!reportedDurations.add(word to madd.phonemeIndex)) continue
                val measured = heardOnsets[heardIndex + 1] - heardOnsets[heardIndex]
                val expected = madd.harakat * harakaSeconds
                val check = judgeDuration(word, madd, measured, expected, harakaSeconds)
                events += TrackerEvent.Duration(check)
            }
        }
    }

    // A deviation of more than one haraka is a deviation; anything closer is fine.
    private fun judgeDuration(
        word: Int,
        madd: MaddAnnotation,
        measured: Float,
        expected: Float,
        harakaSeconds: Float,
    ): DurationCheck {
        val verdict =
            when {
                measured < expected - harakaSeconds -> DurationVerdict.Underheld
                measured > expected + harakaSeconds -> DurationVerdict.Overheld
                else -> DurationVerdict.Ok
            }
        return DurationCheck(word, madd.letter, madd.rule, measured, expected, verdict)
    }

    private fun isGreen(wordStatistics: WordStatistics, word: Int): Boolean {
        val coverage = wordStatistics.aligned.toFloat() / wordLengths[word]
        return coverage >= thresholds.greenCoverage &&
            wordStatistics.substitutions <= thresholds.maximumGreenSubstitutions
    }

    // A green word stays green and a red word stays red: the first clear verdict is final.
    private fun changeState(word: Int, newState: WordState, events: MutableList<TrackerEvent>) {
        val oldState = states[word]
        if (oldState == newState || oldState.isDone()) return
        states[word] = newState
        events += TrackerEvent.WordChanged(word, newState)
    }

    // ---- Insertions ------------------------------------------------------------------------

    private fun reportInsertion(
        operations: List<AlignmentOperation>,
        expectedStart: Int,
        events: MutableList<TrackerEvent>,
    ) {
        var runStart = -1
        var runLength = 0
        var wordBeforeRun = anchorIndex - 1
        var latestWord = anchorIndex - 1
        for (operation in operations) {
            if (operation.step == AlignmentStep.Deletion) continue
            if (operation.step == AlignmentStep.Match) {
                runLength = 0
                latestWord = wordAtExpectedOffset(expectedStart + operation.expectedIndex)
                continue
            }
            if (runLength == 0) {
                runStart = operation.heardIndex
                wordBeforeRun = latestWord
            }
            runLength++
            if (operation.step == AlignmentStep.Substitution) {
                latestWord = wordAtExpectedOffset(expectedStart + operation.expectedIndex)
            }
            val absoluteStart = droppedHeardCount + runStart
            if (
                runLength >= thresholds.insertionPhonemes &&
                    absoluteStart > lastReportedInsertionEnd
            ) {
                lastReportedInsertionEnd = droppedHeardCount + operation.heardIndex
                events += TrackerEvent.Inserted(maxOf(wordBeforeRun, 0))
            }
        }
    }

    private fun dropHeard(count: Int) {
        heardIds = heardIds.subList(count, heardIds.size).toMutableList()
        heardOnsets = heardOnsets.subList(count, heardOnsets.size).toMutableList()
        droppedHeardCount += count
    }

    // ---- Settling ----------------------------------------------------------------------------

    // Drops the heard phonemes that belong to settled words and moves the anchor behind them.
    private fun settleWords(operations: List<AlignmentOperation>, expectedStart: Int) {
        var newAnchor = anchorIndex
        while (newAnchor < reference.size && settled[newAnchor]) newAnchor++
        if (newAnchor == anchorIndex) return
        var dropCount = 0
        for (operation in operations) {
            val isAligned =
                operation.step == AlignmentStep.Match ||
                    operation.step == AlignmentStep.Substitution
            if (!isAligned) continue
            if (wordAtExpectedOffset(expectedStart + operation.expectedIndex) >= newAnchor) {
                dropCount = operation.heardIndex
                break
            }
            dropCount = operation.heardIndex + 1
        }
        dropHeard(dropCount)
        anchorIndex = newAnchor
    }

    // ---- Re-anchoring ------------------------------------------------------------------------

    // Searches the whole reference for the heard tail when the alignment keeps failing.
    private fun reanchorIfLost(
        operations: List<AlignmentOperation>,
        events: MutableList<TrackerEvent>,
    ) {
        if (!isTailFailing(operations)) return
        val searchLength = minOf(REANCHOR_SEARCH_PHONEMES, heardIds.size)
        val tail = heardIds.subList(heardIds.size - searchLength, heardIds.size)
        val place = PhonemeAligner.findBestPlace(tail.toIntArray(), expectedIds) ?: return
        if (
            place.score <
                REANCHOR_MINIMUM_SCORE_SHARE * AlignmentScoring.MATCH * thresholds.reanchorPhonemes
        ) {
            return
        }
        if (place.score < tailScore(operations, searchLength) + REANCHOR_MINIMUM_GAIN) return
        val newAnchor = wordAtExpectedOffset(place.expectedStart)
        if (newAnchor == anchorIndex) return
        clearJudgementsFrom(minOf(anchorIndex, newAnchor), newAnchor, events)
        dropHeard(heardIds.size - searchLength + place.heardStart)
        anchorIndex = newAnchor
        events += TrackerEvent.Anchored(newAnchor)
        // The heard phonemes that found the place are judged against it straight away.
        events += alignAndReport()
    }

    // Words from [newAnchor] on are judged again; earlier unsettled words keep only a green
    // verdict.
    private fun clearJudgementsFrom(
        firstWord: Int,
        newAnchor: Int,
        events: MutableList<TrackerEvent>,
    ) {
        for (word in firstWord until reference.size) {
            if (word >= newAnchor) {
                settled[word] = false
                changeStateToNeutral(word, events)
            } else if (!settled[word] && states[word] != WordState.Confirmed) {
                changeStateToNeutral(word, events)
            }
        }
    }

    private fun changeStateToNeutral(word: Int, events: MutableList<TrackerEvent>) {
        if (states[word] == WordState.Neutral) return
        states[word] = WordState.Neutral
        events += TrackerEvent.WordChanged(word, WordState.Neutral)
    }

    // Chance matches keep a long run of wrong sounds from being strictly unmatched, so the tail
    // fails to align when fewer than MAXIMUM_MATCH_SHARE_WHEN_LOST of its phonemes matched.
    private fun isTailFailing(operations: List<AlignmentOperation>): Boolean {
        val tail = heardTail(operations, thresholds.reanchorPhonemes)
        if (tail.size < thresholds.reanchorPhonemes) return false
        return tail.count { it.step == AlignmentStep.Match } <=
            MAXIMUM_MATCH_SHARE_WHEN_LOST * tail.size
    }

    private fun heardTail(operations: List<AlignmentOperation>, length: Int) =
        operations.filter { it.step != AlignmentStep.Deletion }.takeLast(length)

    // Score of the current alignment over the same tail, to compare with the best place found.
    private fun tailScore(operations: List<AlignmentOperation>, length: Int): Int =
        heardTail(operations, length).sumOf {
            when (it.step) {
                AlignmentStep.Match -> AlignmentScoring.MATCH
                AlignmentStep.Substitution -> AlignmentScoring.SUBSTITUTION
                else -> AlignmentScoring.INSERTION_OPEN
            }
        }

    private companion object {
        const val NO_SYMBOL = -1
        const val BLANK_SYMBOL = "<blank>"

        // The window holds the ayah of the anchor and this many ayahs after it.
        const val AYAHS_AHEAD_IN_WINDOW = 2

        // A word counts as passed when a later word has at least this many aligned phonemes.
        const val PASSED_EVIDENCE_PHONEMES = 2

        // A passed word with fewer matched phonemes than this share is more likely noise aligned by
        // chance than a mispronounced word, so the tracker does not move on until later words
        // are heard well.
        const val MINIMUM_MATCH_SHARE_TO_SETTLE = 0.5f

        // A tail with at most this share of matched phonemes is worth searching the surah for.
        const val MAXIMUM_MATCH_SHARE_WHEN_LOST = 0.75f

        // Re-anchoring searches the surah with at most this many of the latest phonemes ...
        const val REANCHOR_SEARCH_PHONEMES = 24

        // ... and accepts a place that scores this share of a perfect match of as many phonemes
        // as it takes to declare the tracker lost.
        const val REANCHOR_MINIMUM_SCORE_SHARE = 0.6f

        // ... and beats the score of the current alignment over the same phonemes by this much.
        const val REANCHOR_MINIMUM_GAIN = 16
    }
}
