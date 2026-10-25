package app.awn.quran.feature.recite

import app.awn.quran.tracker.WordState

/**
 * The rules for hiding and revealing words in hifz mode, kept free of state so they can be tested.
 */
internal object HifzRules {

    /**
     * Returns how a word is drawn. Outside hifz that is what the tracker reports. In hifz a word
     * stays hidden until it is confirmed or the user revealed it; a mispronounced or wrong word is
     * shown too, because the user needs to see what they missed. A tentative word is still hidden
     * since it may turn out wrong.
     */
    fun displayState(
        trackerState: WordState,
        hifzEnabled: Boolean,
        revealed: Boolean = false,
    ): WordState {
        if (!hifzEnabled) return trackerState
        return when (trackerState) {
            WordState.Confirmed,
            WordState.Mispronounced,
            WordState.Wrong -> trackerState
            WordState.Neutral,
            WordState.Tentative,
            WordState.Unread,
            WordState.Hidden -> if (revealed) WordState.Neutral else WordState.Hidden
        }
    }

    /**
     * The grade a recitation earns by itself: Good when no word was missed, Hard for one, Forgot
     * for two or more. Easy is never given automatically, because only the user knows it was
     * effortless. :feature:learn applies the same numbers to the revision schedule.
     */
    fun suggestedGrade(missedWords: Int): AyahGrade =
        when {
            missedWords <= 0 -> AyahGrade.Good
            missedWords == 1 -> AyahGrade.Hard
            else -> AyahGrade.Forgot
        }

    /**
     * Returns the first letter of [wordText] for the hint. Marks that sit on top of letters (short
     * vowels, shadda) are not letters, so the result is the base letter without them.
     */
    fun firstLetter(wordText: String): String? {
        val letter = wordText.firstOrNull { Character.isLetter(it) } ?: return null
        return letter.toString()
    }
}
