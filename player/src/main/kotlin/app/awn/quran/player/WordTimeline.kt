package app.awn.quran.player

import app.awn.quran.core.data.WordTiming

/** The word timings of one ayah file, looked up by playback position. */
class WordTimeline(words: List<WordTiming>) {

    private val sortedWords = words.sortedBy { it.startMs }

    val isEmpty: Boolean
        get() = sortedWords.isEmpty()

    fun timingOf(position: Int): WordTiming? = sortedWords.firstOrNull { it.position == position }

    /**
     * Returns the word being recited at [positionMs], or null before the first word starts. The
     * position is rounded down to [RESOLUTION_MS], so the answer changes at most every 50 ms. In a
     * gap between two words, and after the last word, the previous word stays current.
     */
    fun wordPositionAt(positionMs: Long): Int? {
        val roundedMs = positionMs / RESOLUTION_MS * RESOLUTION_MS
        return sortedWords.lastOrNull { it.startMs <= roundedMs }?.position
    }

    companion object {
        const val RESOLUTION_MS = 50L
    }
}
