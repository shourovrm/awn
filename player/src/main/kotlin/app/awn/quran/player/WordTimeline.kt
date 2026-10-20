package app.awn.quran.player

import app.awn.quran.core.data.WordTiming

/** The word timings of one ayah file, looked up by playback position. */
class WordTimeline(words: List<WordTiming>) {

    private val sortedWords = words.sortedBy { it.startMs }

    val isEmpty: Boolean
        get() = sortedWords.isEmpty()

    fun timingOf(position: Int): WordTiming? = sortedWords.firstOrNull { it.position == position }

    /**
     * Returns the word being recited at [positionMs], or null before the first word starts and when
     * the reciter is silent: a word stays current until [HOLD_AFTER_END_MS] after its end, so the
     * usual 10 to 250 ms between words keeps one word lit, while a pause of seconds (for example
     * 5:106, 17.3 s to 21.9 s in the Alafasy file) lights nothing.
     */
    fun wordPositionAt(positionMs: Long): Int? {
        val started = sortedWords.lastOrNull { it.startMs <= positionMs } ?: return null
        return started.position.takeIf { positionMs <= started.endMs + HOLD_AFTER_END_MS }
    }

    companion object {
        /** How often playback publishes its position, and so the finest step of the highlight. */
        const val RESOLUTION_MS = 50L

        /** How long a word stays current after its end. */
        const val HOLD_AFTER_END_MS = 300L
    }
}
