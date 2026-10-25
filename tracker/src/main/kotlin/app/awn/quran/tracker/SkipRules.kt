package app.awn.quran.tracker

/**
 * When the cursor word does not pass, the cursor may still move on if later words prove the user
 * went past it. The proof must be stronger than a pass: a word of 4 phonemes or more one or two
 * words on that starts with its first two sounds and passes at
 * [AlignmentThresholds.skipAllowedCost], or two words in a row that each pass. The target may not
 * use audio that matched the cursor word. So at most two words are jumped over in one step. Two
 * cases go further, because the evidence for them is of a different kind:
 * - a whole ayah left out: the cursor is at the start of an ayah and the first words of the next
 *   ayah are heard;
 * - going on after a repeat: the words up to the first word not yet said already have their marks,
 *   so jumping back to it marks nothing;
 * - an ayah left out whose opening words are the same as the next ayah's.
 *
 * Among the candidates the one heard first wins.
 */
internal class SkipRules(
    private val tables: ReferenceTables,
    private val candidates: WordCandidates,
    private val judge: WordJudge,
) {
    /** The best skip, or null; [cursorOutcome] is the cursor word fitted on its own. */
    fun find(context: CursorContext, cursorOutcome: WordOutcome): CursorStep.Skip? {
        val cursor = context.cursor
        val insertion = judge.thresholds.insertionPhonemes
        // Each target with the most unexplained audio allowed before it: what the jumped words
        // could have taken plus an insertion, or for a left-out ayah an insertion only.
        val targets = mutableMapOf<Int, Int>()
        for (target in listOf(cursor + 1, cursor + 2)) {
            targets[target] = phonemesBetween(cursor, target) + insertion
        }
        firstUnsaidWord(context)?.let {
            if (it > cursor + 2) targets[it] = phonemesBetween(cursor, it) + insertion
        }
        val leftOutAyahTarget = nextAyahStartAfterLeftOutAyah(context)?.takeIf { it > cursor + 2 }
        if (leftOutAyahTarget != null) targets[leftOutAyahTarget] = insertion
        val options = targets.flatMap { (target, allowedBefore) ->
            listOfNotNull(single(context, target), pair(context, target)).filter {
                it.fit.heardStart - context.carriedCount <= allowedBefore &&
                    !takesCursorAudio(it, cursorOutcome)
            }
        }
        val best =
            options.minWithOrNull(
                compareBy<CursorStep.Skip>({ it.fit.heardStart }, { it.words.first }),
            )
        return when {
            best != null && best.words.first == leftOutAyahTarget -> best.copy(isLeftOutAyah = true)
            best != null -> best
            else -> skipOverAyahWithSharedOpening(context)
        }
    }

    // Two ayahs that open with the same words: the user who leaves out the first goes on with the
    // second, and its opening words were taken as the first ayah's. When the second ayah goes on
    // past them, the first was left out: its words are marked so, the shared words belong to the
    // second ayah, and the cursor goes on there. The one rule that turns green words red.
    private fun skipOverAyahWithSharedOpening(context: CursorContext): CursorStep.Skip? {
        val cursor = context.cursor
        val ayahStart = tables.firstWordOfAyah(cursor)
        val nextStart = (cursor until tables.size).firstOrNull { !tables.sameAyah(it, cursor) }
        if (nextStart == null || !opensLikeNextAyah(context, ayahStart, nextStart)) return null
        val target = nextStart + (cursor - ayahStart)
        return listOfNotNull(single(context, target), pair(context, target))
            .firstOrNull {
                it.fit.heardStart - context.carriedCount <= judge.thresholds.insertionPhonemes
            }
            ?.copy(
                jumped = ayahStart until nextStart,
                isLeftOutAyah = true,
                reassigned = nextStart until target,
            )
    }

    // True when the words said so far of the ayah at [ayahStart] are green and the same, word for
    // word, as the opening of the ayah at [nextStart], which goes on after them.
    private fun opensLikeNextAyah(context: CursorContext, ayahStart: Int, nextStart: Int): Boolean {
        val saidCount = context.cursor - ayahStart
        val nextGoesOn = nextStart + saidCount < tables.size
        return saidCount > 0 &&
            nextGoesOn &&
            (0 until saidCount).all {
                context.states[ayahStart + it] == WordState.Confirmed &&
                    tables
                        .idsOfWords(ayahStart + it, ayahStart + it)
                        .contentEquals(tables.idsOfWords(nextStart + it, nextStart + it))
            }
    }

    // The cursor word may still be what the user is saying: when it is half heard and the target's
    // fit uses audio that matched it, the same sounds would count twice, so the skip waits.
    private fun takesCursorAudio(skip: CursorStep.Skip, cursorOutcome: WordOutcome): Boolean {
        val start = skip.fit.heardStart
        val sharedMatches =
            (0 until cursorOutcome.length).count {
                cursorOutcome.matched[it] && cursorOutcome.alignedHeard[it] >= start
            }
        val isHalfHeard = cursorOutcome.matches * 2 >= cursorOutcome.length
        return isHalfHeard && sharedMatches >= SHARED_MATCHES_FOR_DOUBT
    }

    private fun phonemesBetween(firstWord: Int, endWord: Int): Int =
        (firstWord until minOf(endWord, tables.size)).sumOf { tables.wordLengths[it] }

    // A target word on its own must be long enough not to match by chance, and pass in full.
    private fun single(context: CursorContext, target: Int): CursorStep.Skip? {
        val isLongEnough = target < tables.size && !judge.isShort(target)
        val fit = if (isLongEnough) candidates.fit(context.heard, target, target) else null
        val outcome = fit?.outcomeOf(target)
        val provesSkip =
            outcome != null && outcome.startsCleanly && judge.passesAsSkipEvidence(outcome)
        return if (provesSkip) {
            CursorStep.Skip(context.cursor until target, checkNotNull(fit), target..target)
        } else {
            null
        }
    }

    private fun pair(context: CursorContext, target: Int): CursorStep.Skip? {
        val fit = candidates.fit(context.heard, target, target + 1) ?: return null
        val bothPass =
            fit.outcomes.first().startsCleanly &&
                fit.outcomes.all { judge.verdict(it) == WordVerdict.Pass }
        return if (bothPass) CursorStep.Skip(context.cursor until target, fit, target..target + 1)
        else null
    }

    // While the user repeats, the words after the cursor already have their marks; the first word
    // without one is where they go on.
    private fun firstUnsaidWord(context: CursorContext): Int? =
        (context.cursor until tables.size).firstOrNull { !context.isSettled(it) }

    // The first word of the ayah after the cursor's, when the cursor is at the start of an ayah
    // that has not been said at all.
    private fun nextAyahStartAfterLeftOutAyah(context: CursorContext): Int? {
        val cursor = context.cursor
        val ayah = tables.ayahSequenceOf(cursor)
        val startsAyah = cursor == 0 || tables.ayahSequenceOf(cursor - 1) != ayah
        val nextStart = (cursor until tables.size).firstOrNull { tables.ayahSequenceOf(it) != ayah }
        val untouched =
            nextStart != null &&
                (cursor until nextStart).all {
                    context.states[it] != WordState.Confirmed &&
                        context.states[it] != WordState.Wrong
                }
        return if (startsAyah && untouched) nextStart else null
    }

    private companion object {
        // This many of the cursor word's matched sounds inside a target's audio make the skip wait.
        const val SHARED_MATCHES_FOR_DOUBT = 2
    }
}

private fun ReferenceTables.sameAyah(word: Int, other: Int) =
    ayahSequenceOf(word) == ayahSequenceOf(other)

private fun ReferenceTables.firstWordOfAyah(word: Int): Int {
    var first = word
    while (first > 0 && sameAyah(first - 1, word)) first--
    return first
}
