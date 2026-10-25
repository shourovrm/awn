package app.awn.quran.tracker

/**
 * Notices that the user went back and says a word already passed: one of the last [RECENT_WORDS]
 * words, or the first word of the cursor's ayah or of the ayah before (a restarted or a repeated
 * ayah). The word must pass at [AlignmentThresholds.skipAllowedCost], begin at the front of the
 * unexplained audio, and be followed by the start of the word after it.
 */
internal class RepeatDetector(
    private val tables: ReferenceTables,
    private val candidates: WordCandidates,
    private val judge: WordJudge,
) {
    fun find(context: CursorContext): CursorStep.Repeat? {
        val cursor = context.cursor.coerceAtMost(tables.size)
        val starts =
            listOf(
                    cursor - 1,
                    cursor - 2,
                    cursor - RECENT_WORDS,
                    firstWordOfAyah(cursor - 1),
                    firstWordOfAyah(cursor),
                )
                .filter { it in 0 until cursor && context.isSettled(it) }
                .distinct()
                .sortedDescending()
        return starts.firstNotNullOfOrNull { repeatAt(context, it) }
    }

    // The repeated word must pass at the stricter skip cost and the word after it must have begun,
    // so the start of the cursor word, which often sounds like an earlier word, is not taken for a
    // repeat.
    private fun repeatAt(context: CursorContext, word: Int): CursorStep.Repeat? {
        val fit = candidates.fit(context.heard, word, word + 1) ?: return null
        val outcome = fit.outcomeOf(word)
        val passes = judge.passesAsSkipEvidence(outcome)
        val followed = fit.outcomeOf(word + 1).aligned >= NEXT_WORD_EVIDENCE
        val atFront = context.startsAtFront(outcome.firstHeard)
        return if (passes && followed && atFront) CursorStep.Repeat(fit, word) else null
    }

    private fun firstWordOfAyah(word: Int): Int {
        if (word !in 0 until tables.size) return -1
        val ayah = tables.ayahSequenceOf(word)
        var first = word
        while (first > 0 && tables.ayahSequenceOf(first - 1) == ayah) first--
        return first
    }

    private companion object {
        const val RECENT_WORDS = 3
        const val NEXT_WORD_EVIDENCE = 2
    }
}
