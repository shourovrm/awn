package app.awn.quran.search

import app.awn.quran.tracker.AyahMatch
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.TrackerInput

/**
 * Listens to one voice search: feeds the microphone to the speech model and searches the Quran with
 * everything heard so far once a second, so the matches can be shown while the user recites.
 *
 * Listening ends when the microphone stops (the user tapped stop), after [MAXIMUM_BLOCKS], or once
 * the same ayah led three searches in a row and at least five seconds were heard. Five seconds
 * found the right ayah among the first three for 98% of 102 test recordings
 * (AyahFinderAccuracyTest); three seconds found it for 71 to 90%.
 */
class VoiceListening(
    private val input: TrackerInput,
    private val source: PhonemeSource,
    private val findAyahs: (heardSymbols: List<String>) -> List<AyahMatch>,
) {

    /**
     * Blocks until listening ends, calling [onMatches] after every search, and returns the matches
     * of the last search, which includes what the model decodes after the last block.
     */
    fun run(onMatches: (List<AyahMatch>) -> Unit): List<AyahMatch> {
        val heard = mutableListOf<String>()
        val leaders = mutableListOf<AyahMatch?>()
        var blockCount = 0
        while (blockCount < MAXIMUM_BLOCKS && !isSettled(leaders, blockCount)) {
            val block = input.nextBlock() ?: break
            blockCount++
            heard += source.accept(block).map { it.symbol }
            if (blockCount % BLOCKS_PER_SEARCH == 0) {
                val matches = findAyahs(heard)
                leaders += matches.firstOrNull()
                onMatches(matches)
            }
        }
        heard += decodeRemainingAudio()
        val finalMatches = findAyahs(heard)
        onMatches(finalMatches)
        return finalMatches
    }

    private fun isSettled(leaders: List<AyahMatch?>, blockCount: Int): Boolean {
        if (blockCount < MINIMUM_BLOCKS || leaders.size < SETTLED_SEARCHES) return false
        val lastLeaders = leaders.takeLast(SETTLED_SEARCHES).map { it?.surah to it?.ayah }
        return lastLeaders.first().first != null && lastLeaders.distinct().size == 1
    }

    // The streaming model decodes in 0.48 s chunks, so the end of the recitation only comes out
    // after some silence; a live microphone would give it, a stopped one does not.
    private fun decodeRemainingAudio(): List<String> {
        val symbols = mutableListOf<String>()
        repeat(SILENCE_BLOCKS) {
            val silence = FloatArray(TrackerInput.SAMPLE_RATE_HZ / BLOCKS_PER_SECOND)
            symbols += source.accept(silence).map { it.symbol }
        }
        symbols += source.finish().map { it.symbol }
        return symbols
    }

    companion object {
        /** The microphone gives 100 ms blocks. */
        private const val BLOCKS_PER_SECOND = 10
        private const val BLOCKS_PER_SEARCH = BLOCKS_PER_SECOND
        private const val MINIMUM_BLOCKS = 5 * BLOCKS_PER_SECOND
        private const val SETTLED_SEARCHES = 3
        private const val SILENCE_BLOCKS = BLOCKS_PER_SECOND

        /** Fifteen seconds; longer adds little, because the first seconds decide the match. */
        const val MAXIMUM_BLOCKS = 15 * BLOCKS_PER_SECOND
    }
}
