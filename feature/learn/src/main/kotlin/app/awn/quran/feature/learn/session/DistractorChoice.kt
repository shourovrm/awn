package app.awn.quran.feature.learn.session

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * Chooses the wrong answers of a question. No part-of-speech data exists (see docs/decisions/
 * learn.md), so a wrong answer is plausible when its form is about as frequent as the right one
 * and about as long. Two answers never share a meaning, because the user could not tell them apart.
 */
object DistractorChoice {

    /** How strongly a difference of one letter counts against a candidate, next to rank closeness. */
    private const val LETTER_WEIGHT = 0.3

    /** Random noise added to every score so the same word does not always get the same options. */
    private const val NOISE_WEIGHT = 0.25

    /** The most candidates a caller should look up before choosing: enough for a good pick. */
    const val CANDIDATE_LIMIT = 30

    /**
     * Returns up to [count] of [candidates], the most similar first. A candidate is dropped when
     * it is the [target] itself or when its meaning, ignoring case and surrounding space, equals
     * the meaning of the target or of a candidate already chosen.
     */
    fun choose(target: WordInfo, candidates: List<WordInfo>, count: Int, random: Random): List<WordInfo> {
        val scored =
            candidates
                .filter { it.rank != target.rank && it.meaning.isNotBlank() }
                .map { it to score(target, it, random) }
                .sortedBy { it.second }
                .map { it.first }
        val usedMeanings = mutableSetOf(meaningKey(target))
        val chosen = mutableListOf<WordInfo>()
        for (candidate in scored) {
            if (chosen.size == count) break
            if (usedMeanings.add(meaningKey(candidate))) chosen += candidate
        }
        return chosen
    }

    /**
     * The ranks to look up for [target]: the [limit] nearest ranks on both sides, because a form
     * of a similar frequency is the best distractor.
     */
    fun candidateRanks(target: Int, formCount: Int, limit: Int = CANDIDATE_LIMIT): List<Int> {
        val ranks = mutableListOf<Int>()
        var distance = 1
        // Walk outwards from the target, one rank below then one above, until enough are found or
        // both ends of the ranking are passed.
        while (ranks.size < limit && (target - distance >= 1 || target + distance <= formCount)) {
            if (target - distance >= 1) ranks += target - distance
            if (target + distance <= formCount && ranks.size < limit) ranks += target + distance
            distance++
        }
        return ranks
    }

    private fun score(target: WordInfo, candidate: WordInfo, random: Random): Double {
        val rankDistance = abs(ln(candidate.rank.toDouble()) - ln(target.rank.toDouble()))
        val letterDistance = abs(candidate.letterCount - target.letterCount) * LETTER_WEIGHT
        return rankDistance + letterDistance + random.nextDouble() * NOISE_WEIGHT
    }

    private fun meaningKey(info: WordInfo): String = info.meaning.trim().lowercase()
}
