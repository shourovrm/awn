package app.awn.quran.tracker

/**
 * An ayah that a short recitation may come from; a higher [score] is a closer match. The words the
 * recitation covers in this ayah run from [firstWord] to [lastWord], counted from 0; when the
 * recitation runs on into the next ayah, [lastWord] is the last word of this one.
 */
data class AyahMatch(
    val surah: Int,
    val ayah: Int,
    val score: Int,
    val firstWord: Int,
    val lastWord: Int,
)

/**
 * Finds which ayahs a few seconds of recitation come from, anywhere in the Quran.
 *
 * Every ayah's phonemes are joined into one sequence, so a recitation that runs over the end of an
 * ayah still lines up. [find] runs a local alignment (Smith-Waterman, with the tracker's
 * [AlignmentScoring]) of the heard phonemes against that whole sequence and credits each match to
 * the ayah where it starts, which is the ayah the reader began in. The table is walked one column
 * at a time, so memory stays at two columns of the heard length instead of heard × 650,000.
 *
 * Runs of the same phoneme count once on both sides: the model repeats a held sound, and the
 * reference spells a doubled letter twice, but neither is a difference in what was recited.
 */
class AyahFinder
private constructor(
    private val ayahKeys: List<Pair<Int, Int>>,
    private val phonemeIds: IntArray,
    private val ayahIndexOfPhoneme: IntArray,
    private val wordIndexOfPhoneme: IntArray,
    private val lastWordIndexOfAyah: IntArray,
    private val idsBySymbol: Map<Char, Int>,
) {

    /**
     * The [limit] best ayahs for [heardSymbols] (model token symbols, in order), best first. Empty
     * when too little was heard or nothing matches closely enough to be worth showing.
     */
    fun find(heardSymbols: List<String>, limit: Int = DEFAULT_LIMIT): List<AyahMatch> =
        rankedAboveMinimum(heardSymbols).take(limit)

    /**
     * Every ayah close enough to the best match for [heardSymbols] to be a candidate: each ayah
     * scoring at least [RELATIVE_CUTOFF_SHARE] of the best, equal scores in Quran order, at most
     * [MAXIMUM_RESULTS]. Empty when [find] would be empty.
     */
    fun findAll(heardSymbols: List<String>): List<AyahMatch> {
        val ranked = rankedAboveMinimum(heardSymbols)
        val bestScore = ranked.firstOrNull()?.score ?: return emptyList()
        val cutoffScore = bestScore * RELATIVE_CUTOFF_SHARE
        return ranked.filter { match -> match.score >= cutoffScore }.take(MAXIMUM_RESULTS)
    }

    // Every ayah that clears the minimum score, best first; equal scores keep Quran order.
    private fun rankedAboveMinimum(heardSymbols: List<String>): List<AyahMatch> {
        val heard = heardIdsOf(heardSymbols)
        if (heard.size < MINIMUM_HEARD_PHONEMES) return emptyList()
        val bestByAyah = bestAlignmentByAyah(heard)
        val bestScoreByAyah = bestByAyah.scores
        val minimumScore = (heard.size * AlignmentScoring.MATCH * MINIMUM_SCORE_SHARE).toInt()
        return bestScoreByAyah.indices
            .filter { ayahIndex -> bestScoreByAyah[ayahIndex] >= minimumScore }
            .sortedByDescending { ayahIndex -> bestScoreByAyah[ayahIndex] }
            .map { ayahIndex ->
                val (surah, ayah) = ayahKeys[ayahIndex]
                matchOf(surah, ayah, ayahIndex, bestByAyah)
            }
    }

    private fun matchOf(surah: Int, ayah: Int, ayahIndex: Int, best: BestAlignments): AyahMatch {
        val firstWord = wordIndexOfPhoneme[best.startPhonemes[ayahIndex]]
        val endPhoneme = best.endPhonemes[ayahIndex]
        // A recitation that runs on into the next ayah covers this ayah to its last word.
        val lastWord =
            if (ayahIndexOfPhoneme[endPhoneme] == ayahIndex) {
                wordIndexOfPhoneme[endPhoneme]
            } else {
                lastWordIndexOfAyah[ayahIndex]
            }
        return AyahMatch(surah, ayah, best.scores[ayahIndex], firstWord, lastWord)
    }

    // Per ayah: its best credited score and the reference phonemes (0-based) where that alignment
    // started and ended.
    private class BestAlignments(ayahCount: Int) {
        val scores = IntArray(ayahCount)
        val startPhonemes = IntArray(ayahCount)
        val endPhonemes = IntArray(ayahCount)
    }

    // A heard symbol the reference never uses gets an id that matches nothing.
    private fun heardIdsOf(symbols: List<String>): IntArray {
        val characters = symbols.filter { it != BLANK_SYMBOL }.flatMap { symbol -> symbol.toList() }
        val ids = characters.map { character -> idsBySymbol[character] ?: UNKNOWN_ID }
        return withoutRepeats(ids).toIntArray()
    }

    // Each cell's alignment remembers where it started in the reference, so the score can be
    // credited to the starting ayah. Column j covers the first j reference phonemes.
    private fun bestAlignmentByAyah(heard: IntArray): BestAlignments {
        val best = BestAlignments(ayahKeys.size)
        var previousScores = IntArray(heard.size + 1)
        var currentScores = IntArray(heard.size + 1)
        var previousStarts = IntArray(heard.size + 1)
        var currentStarts = IntArray(heard.size + 1)
        for (referenceCount in 1..phonemeIds.size) {
            val referenceId = phonemeIds[referenceCount - 1]
            currentScores[0] = 0
            currentStarts[0] = referenceCount
            for (heardCount in 1..heard.size) {
                val diagonal = previousScores[heardCount - 1]
                val pairScore =
                    if (heard[heardCount - 1] == referenceId) {
                        AlignmentScoring.MATCH
                    } else {
                        AlignmentScoring.SUBSTITUTION
                    }
                val fromDiagonal = diagonal + pairScore
                val fromAbove = currentScores[heardCount - 1] + AlignmentScoring.INSERTION_OPEN
                val fromLeft = previousScores[heardCount] + AlignmentScoring.DELETION_OPEN
                val score = maxOf(0, fromDiagonal, fromAbove, fromLeft)
                currentScores[heardCount] = score
                // A diagonal step out of a zero cell starts a new alignment at this phoneme.
                currentStarts[heardCount] =
                    when {
                        score == fromDiagonal && diagonal == 0 -> referenceCount - 1
                        score == fromDiagonal -> previousStarts[heardCount - 1]
                        score == fromAbove -> currentStarts[heardCount - 1]
                        else -> previousStarts[heardCount]
                    }
                creditAyah(best, score, currentStarts[heardCount], referenceCount - 1)
            }
            previousScores = currentScores.also { currentScores = previousScores }
            previousStarts = currentStarts.also { currentStarts = previousStarts }
        }
        return best
    }

    // Readers usually start at the beginning of an ayah, so a match that starts there wins a tie
    // with the same phrase in the middle of another ayah ("على صراط مستقيم" ends 36:4 and is
    // inside many others).
    private fun creditAyah(best: BestAlignments, score: Int, startPhoneme: Int, endPhoneme: Int) {
        if (score <= 0) return
        val start = startPhoneme.coerceAtMost(phonemeIds.size - 1)
        val ayahIndex = ayahIndexOfPhoneme[start]
        val startsAyah = start == 0 || ayahIndexOfPhoneme[start - 1] != ayahIndex
        val credited = if (startsAyah) score + AYAH_START_BONUS else score
        if (credited > best.scores[ayahIndex]) {
            best.scores[ayahIndex] = credited
            best.startPhonemes[ayahIndex] = start
            best.endPhonemes[ayahIndex] = endPhoneme
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 3

        // About one second of recitation; fewer phonemes match too many places by chance.
        private const val MINIMUM_HEARD_PHONEMES = 8

        // A match must be worth at least this share of a perfect match of everything heard.
        private const val MINIMUM_SCORE_SHARE = 0.4

        // A fragment ties with every ayah that shares its phonemes, so ayahs close to the best are
        // kept; a lower share lists ayahs that only share a phrase. To be tuned on
        // AyahFinderAccuracyTest.
        private const val RELATIVE_CUTOFF_SHARE = 0.85

        // A fragment can tie with hundreds of ayahs; the cap keeps the list and the screen bounded.
        const val MAXIMUM_RESULTS = 100

        // Worth about two matched phonemes: enough to break a tie, too little to beat a better
        // match.
        private const val AYAH_START_BONUS = 2 * AlignmentScoring.MATCH

        private const val BLANK_SYMBOL = "<blank>"
        private const val UNKNOWN_ID = -1
        private const val LAST_SURAH = 114

        /**
         * Reads every ayah of surahs 1 to [surahCount] from [reference]. An ayah number with no
         * words ends its surah.
         */
        fun from(reference: ReferencePhonemes, surahCount: Int = LAST_SURAH): AyahFinder {
            val idsBySymbol = mutableMapOf<Char, Int>()
            val ayahKeys = mutableListOf<Pair<Int, Int>>()
            val phonemeIds = mutableListOf<Int>()
            val ayahIndexOfPhoneme = mutableListOf<Int>()
            val wordIndexOfPhoneme = mutableListOf<Int>()
            val lastWordIndexOfAyah = mutableListOf<Int>()
            for (surah in 1..surahCount) {
                var ayah = 1
                var words = reference.forAyah(surah, ayah)
                while (words.isNotEmpty()) {
                    val characters = words.flatMap { word ->
                        word.phonemes.joinToString("").toList()
                    }
                    val wordIndexOfCharacter = words.flatMapIndexed { wordIndex, word ->
                        List(word.phonemes.joinToString("").length) { wordIndex }
                    }
                    val ids = characters.map { idsBySymbol.getOrPut(it) { idsBySymbol.size } }
                    val keptCharacters = indexesWithoutRepeats(ids)
                    phonemeIds += keptCharacters.map { ids[it] }
                    wordIndexOfPhoneme += keptCharacters.map { wordIndexOfCharacter[it] }
                    repeat(keptCharacters.size) { ayahIndexOfPhoneme += ayahKeys.size }
                    lastWordIndexOfAyah += words.size - 1
                    ayahKeys += surah to ayah
                    ayah++
                    words = reference.forAyah(surah, ayah)
                }
            }
            return AyahFinder(
                ayahKeys,
                phonemeIds.toIntArray(),
                ayahIndexOfPhoneme.toIntArray(),
                wordIndexOfPhoneme.toIntArray(),
                lastWordIndexOfAyah.toIntArray(),
                idsBySymbol,
            )
        }

        private fun withoutRepeats(ids: List<Int>): List<Int> =
            indexesWithoutRepeats(ids).map {
                ids[it]
            }

        // The positions of [ids] that start a run of one id; a run keeps the word of its first id.
        private fun indexesWithoutRepeats(ids: List<Int>): List<Int> =
            ids.indices.filter { index -> index == 0 || ids[index - 1] != ids[index] }
    }
}
