package app.awn.quran.feature.learn.content

import kotlin.random.Random

/** The snippet on screen and every position shown so far this session, the current one included. */
data class SnippetShown(val currentIndex: Int, val seenIndices: Set<Int>) {
    companion object {
        /** The start of a session: only the day's snippet has been shown. */
        fun startingAt(dayIndex: Int): SnippetShown = SnippetShown(dayIndex, setOf(dayIndex))
    }
}

/** Picks the next snippet to show when the user taps the card. */
object SnippetPicker {

    /**
     * A random position out of [snippetCount] that is not the one on screen and not one seen yet.
     * Once every position has been seen, the round starts again with only the current one counted
     * as seen, so a tap never shows the same snippet twice in a row. With one snippet there is
     * nothing else to show.
     */
    fun next(shown: SnippetShown, snippetCount: Int, random: Random): SnippetShown {
        if (snippetCount <= 1) return shown
        val seenThisRound =
            if (shown.seenIndices.size >= snippetCount) setOf(shown.currentIndex)
            else shown.seenIndices
        val unseenIndices = (0 until snippetCount).filter { it !in seenThisRound }
        val picked = unseenIndices.random(random)
        return SnippetShown(picked, seenThisRound + picked)
    }
}
