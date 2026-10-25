package app.awn.quran.tracker

/** How a word is shown on the page while the user recites. */
enum class WordState {
    Neutral,
    Confirmed,
    Tentative,

    /**
     * The word was said (the tracker confirmed it) but with a wrong sound or a vowel held too short
     * or too long. For display only: the tracker never reports it, the recite session publishes it.
     */
    Mispronounced,
    Wrong,
    Hidden,

    /**
     * The user went on further ahead and did not recite this word: drawn without a mark, not a
     * mistake, and not counted by Hifz.
     */
    Unread,
}

/**
 * What went wrong with a word the user recited, in plain terms, for the card that opens when the
 * word is tapped.
 */
enum class WordProblem {
    /** The word was never heard. */
    Skipped,

    /** The word was said, but with a wrong sound or a vowel held too short or too long. */
    Mispronounced,

    /** Something other than this word was heard in its place. */
    DifferentWord,
}
