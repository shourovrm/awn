package app.awn.quran.tracker

/** What the tracker sees when it decides the next step. */
internal class CursorContext(
    val heard: IntArray,
    val carriedCount: Int,
    val cursor: Int,
    val states: Array<WordState>,
    /** The first word of this microphone run; nothing before it was heard in this run. */
    val runStart: Int,
) {
    /** True when a fit that starts at heard index [start] begins with the unexplained audio. */
    fun startsAtFront(start: Int): Boolean = start in 0..carriedCount + FRONT_SLACK_PHONEMES

    fun isSettled(word: Int): Boolean =
        states[word] == WordState.Confirmed || states[word] == WordState.Wrong

    private companion object {
        // A stray sound or two before a word still counts as the front of the heard audio.
        const val FRONT_SLACK_PHONEMES = 2
    }
}

/** One move of the cursor, decided from the heard phonemes that no word has taken yet. */
internal sealed interface CursorStep {
    /** The words [words] of [fit] were said, from the cursor on. */
    data class Say(val fit: CandidateFit, val words: IntRange) : CursorStep

    /**
     * The words [words] of [fit] were said, after [jumped] were left out or said wrong; all of
     * [jumped] were left out when [isLeftOutAyah]. [reassigned] are words of the next ayah whose
     * audio was taken by the same words at the start of [jumped] (see [SkipRules]).
     */
    data class Skip(
        val jumped: IntRange,
        val fit: CandidateFit,
        val words: IntRange,
        val isLeftOutAyah: Boolean = false,
        val reassigned: IntRange = IntRange.EMPTY,
    ) : CursorStep

    /** The word [word] of [fit], already passed, was said again. */
    data class Repeat(val fit: CandidateFit, val word: Int) : CursorStep

    /** The cursor word was said but for its last consonant; it waits. */
    data class Hold(val fit: CandidateFit) : CursorStep

    data object Wait : CursorStep
}

/**
 * Picks the next step. Only the cursor word, the two words after it and the words just passed are
 * fitted, each on its own with free audio around it, so no step can colour more than two words red
 * (the one exception, a whole ayah left out, is in [SkipRules]).
 */
internal class CursorDecisions(
    tables: ReferenceTables,
    private val candidates: WordCandidates,
    private val judge: WordJudge,
) {
    private val skipRules = SkipRules(tables, candidates, judge)
    private val repeatDetector = RepeatDetector(tables, candidates, judge)

    // Each early return is one rule in the order of precedence; nesting them would hide the order.
    @Suppress("ReturnCount")
    fun decide(context: CursorContext): CursorStep {
        val cursor = context.cursor
        val cursorFit = candidates.fit(context.heard, cursor, cursor) ?: return CursorStep.Wait
        // The audio does not start with the cursor word: perhaps the user went back.
        if (!context.startsAtFront(cursorFit.heardStart)) {
            repeatDetector.find(context)?.let {
                return it
            }
        }
        val atCursor = sayAtCursor(context, cursorFit)
        if (atCursor is CursorStep.Say) return atCursor
        val skip = skipRules.find(context, cursorFit.outcomeOf(cursor))
        if (skip != null) return skip
        return atCursor ?: repeatDetector.find(context) ?: CursorStep.Wait
    }

    // Say when the cursor word passes, Hold when it waits for its tail, null otherwise.
    private fun sayAtCursor(context: CursorContext, cursorFit: CandidateFit): CursorStep? {
        val cursor = context.cursor
        val outcome = cursorFit.outcomeOf(cursor)
        val withNext = candidates.fit(context.heard, cursor, cursor + 1)
        if (judge.isShort(cursor)) return shortWordStep(context, cursorFit, withNext)
        return when (judge.verdict(outcome)) {
            WordVerdict.Pass -> CursorStep.Say(cursorFit, cursor..cursor)
            WordVerdict.Held ->
                if (withNext != null && withNext.outcomeOf(cursor + 1).startsCleanly) {
                    CursorStep.Say(cursorFit, cursor..cursor)
                } else {
                    CursorStep.Hold(cursorFit)
                }
            WordVerdict.Fail -> saidWithNextWord(cursor, withNext)
        }
    }

    // The cursor word fitted together with the next one: a word whose end runs into the next word
    // (wasl, idgham) or whose own fit landed on the wrong stretch of audio.
    private fun saidWithNextWord(cursor: Int, withNext: CandidateFit?): CursorStep? {
        if (withNext == null) return null
        val next = withNext.outcomeOf(cursor + 1)
        val verdict = judge.verdict(withNext.outcomeOf(cursor))
        val closed =
            verdict == WordVerdict.Pass || (verdict == WordVerdict.Held && next.startsCleanly)
        return if (closed && next.aligned >= NEXT_WORD_EVIDENCE_PHONEMES) {
            CursorStep.Say(withNext, cursor..cursor)
        } else {
            null
        }
    }

    // A word of 1 to 3 phonemes matches by chance too easily to be accepted alone: it passes when
    // heard exactly at the front of the audio, or between a said word and a heard next word.
    private fun shortWordStep(
        context: CursorContext,
        cursorFit: CandidateFit,
        withNext: CandidateFit?,
    ): CursorStep? {
        val cursor = context.cursor
        val outcome = cursorFit.outcomeOf(cursor)
        val exactAtFront = judge.isExact(outcome) && context.startsAtFront(cursorFit.heardStart)
        val boundedBefore =
            cursor == 0 ||
                cursor == context.runStart ||
                context.states[cursor - 1] == WordState.Confirmed
        val boundedAfter =
            withNext != null &&
                judge.verdict(withNext.outcomeOf(cursor)) == WordVerdict.Pass &&
                withNext.outcomeOf(cursor + 1).aligned >= NEXT_WORD_EVIDENCE_PHONEMES
        return when {
            exactAtFront -> CursorStep.Say(cursorFit, cursor..cursor)
            boundedBefore && boundedAfter -> CursorStep.Say(checkNotNull(withNext), cursor..cursor)
            else -> null
        }
    }

    private companion object {
        // The next word has begun once this many of its phonemes are aligned.
        const val NEXT_WORD_EVIDENCE_PHONEMES = 2
    }
}
