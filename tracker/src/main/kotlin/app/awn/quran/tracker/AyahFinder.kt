package app.awn.quran.tracker

/** An ayah that a short recitation may come from; a higher [score] is a closer match. */
data class AyahMatch(val surah: Int, val ayah: Int, val score: Int)

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
    private val idsBySymbol: Map<Char, Int>,
) {

    /**
     * The [limit] best ayahs for [heardSymbols] (model token symbols, in order), best first. Empty
     * when too little was heard or nothing matches closely enough to be worth showing.
     */
    fun find(heardSymbols: List<String>, limit: Int = DEFAULT_LIMIT): List<AyahMatch> {
        val heard = heardIdsOf(heardSymbols)
        if (heard.size < MINIMUM_HEARD_PHONEMES) return emptyList()
        val bestScoreByAyah = bestScoreByAyah(heard)
        val minimumScore = (heard.size * AlignmentScoring.MATCH * MINIMUM_SCORE_SHARE).toInt()
        return bestScoreByAyah.indices
            .filter { ayahIndex -> bestScoreByAyah[ayahIndex] >= minimumScore }
            .sortedByDescending { ayahIndex -> bestScoreByAyah[ayahIndex] }
            .take(limit)
            .map { ayahIndex ->
                val (surah, ayah) = ayahKeys[ayahIndex]
                AyahMatch(surah, ayah, bestScoreByAyah[ayahIndex])
            }
    }

    // A heard symbol the reference never uses gets an id that matches nothing.
    private fun heardIdsOf(symbols: List<String>): IntArray {
        val characters = symbols.filter { it != BLANK_SYMBOL }.flatMap { symbol -> symbol.toList() }
        val ids = characters.map { character -> idsBySymbol[character] ?: UNKNOWN_ID }
        return withoutRepeats(ids).toIntArray()
    }

    // Each cell's alignment remembers where it started in the reference, so the score can be
    // credited to the starting ayah. Column j covers the first j reference phonemes.
    private fun bestScoreByAyah(heard: IntArray): IntArray {
        val bestScores = IntArray(ayahKeys.size)
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
                val best = maxOf(0, fromDiagonal, fromAbove, fromLeft)
                currentScores[heardCount] = best
                // A diagonal step out of a zero cell starts a new alignment at this phoneme.
                currentStarts[heardCount] =
                    when {
                        best == fromDiagonal && diagonal == 0 -> referenceCount - 1
                        best == fromDiagonal -> previousStarts[heardCount - 1]
                        best == fromAbove -> currentStarts[heardCount - 1]
                        else -> previousStarts[heardCount]
                    }
                creditAyah(bestScores, best, currentStarts[heardCount])
            }
            previousScores = currentScores.also { currentScores = previousScores }
            previousStarts = currentStarts.also { currentStarts = previousStarts }
        }
        return bestScores
    }

    // Readers usually start at the beginning of an ayah, so a match that starts there wins a tie
    // with the same phrase in the middle of another ayah ("على صراط مستقيم" ends 36:4 and is
    // inside many others).
    private fun creditAyah(bestScores: IntArray, score: Int, startPhoneme: Int) {
        if (score <= 0) return
        val start = startPhoneme.coerceAtMost(phonemeIds.size - 1)
        val ayahIndex = ayahIndexOfPhoneme[start]
        val startsAyah = start == 0 || ayahIndexOfPhoneme[start - 1] != ayahIndex
        val credited = if (startsAyah) score + AYAH_START_BONUS else score
        if (credited > bestScores[ayahIndex]) bestScores[ayahIndex] = credited
    }

    companion object {
        const val DEFAULT_LIMIT = 3

        // About one second of recitation; fewer phonemes match too many places by chance.
        private const val MINIMUM_HEARD_PHONEMES = 8

        // A match must be worth at least this share of a perfect match of everything heard.
        private const val MINIMUM_SCORE_SHARE = 0.4

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
            for (surah in 1..surahCount) {
                var ayah = 1
                var words = reference.forAyah(surah, ayah)
                while (words.isNotEmpty()) {
                    val characters = words.flatMap { word ->
                        word.phonemes.joinToString("").toList()
                    }
                    val ids = characters.map { idsBySymbol.getOrPut(it) { idsBySymbol.size } }
                    val collapsed = withoutRepeats(ids)
                    phonemeIds += collapsed
                    repeat(collapsed.size) { ayahIndexOfPhoneme += ayahKeys.size }
                    ayahKeys += surah to ayah
                    ayah++
                    words = reference.forAyah(surah, ayah)
                }
            }
            return AyahFinder(
                ayahKeys,
                phonemeIds.toIntArray(),
                ayahIndexOfPhoneme.toIntArray(),
                idsBySymbol,
            )
        }

        private fun withoutRepeats(ids: List<Int>): List<Int> = ids.filterIndexed { index, id ->
            index == 0 || ids[index - 1] != id
        }
    }
}
