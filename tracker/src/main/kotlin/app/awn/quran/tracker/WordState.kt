package app.awn.quran.tracker

/** How a word is shown on the page while the user recites. */
enum class WordState {
    Neutral,
    Confirmed,
    Tentative,
    Wrong,
    Hidden,
}

/**
 * What went wrong with a word the user recited, in plain terms, for the card that opens when the
 * word is tapped.
 */
enum class WordProblem {
    /** The word was never heard. */
    Skipped,

    /** The word was heard but not clearly enough to confirm. */
    NotClear,

    /** Something other than this word was heard in its place. */
    DifferentWord,
}
