package app.awn.quran.search

import app.awn.quran.tracker.AyahMatch
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.TrackerInput

/** The ayahs for all audio heard so far. [heardTooLittle] means too little audio to search. */
data class VoiceSearchResult(val matches: List<AyahMatch>, val heardTooLittle: Boolean)

/**
 * Listens to one voice search. The user recites in parts, so the audio is searched at each pause in
 * the speech, with all the audio heard so far, and the microphone stays open for the next part.
 *
 * Listening ends when the microphone stops (the user tapped stop), after [SILENCE_END_BLOCKS] quiet
 * blocks that follow a search, or at [MAXIMUM_BLOCKS].
 */
class VoiceListening(
    private val input: TrackerInput,
    private val source: PhonemeSource,
    private val findAyahs: (heardSymbols: List<String>) -> List<AyahMatch>,
) {

    /**
     * Blocks until listening ends, calling [onResult] after every search. When the audio ends with
     * speech that no pause has searched yet, the last search covers it; so does a stop before any
     * search, which then reports too little heard.
     */
    fun run(onResult: (VoiceSearchResult) -> Unit) {
        val state = ListeningState()
        while (state.isListening && state.blockCount < MAXIMUM_BLOCKS) {
            val block = input.nextBlock()
            if (block == null) state.isListening = false else listenToBlock(state, block, onResult)
        }
        if (state.speechSinceSearch || !state.hasSearched) {
            state.heard += trailingSilenceSymbols()
            state.heard += source.finish().map { it.symbol }
            onResult(resultFor(state.heard))
        }
    }

    private fun listenToBlock(
        state: ListeningState,
        block: FloatArray,
        onResult: (VoiceSearchResult) -> Unit,
    ) {
        state.blockCount++
        state.heard += source.accept(block).map { it.symbol }
        when (state.pauseDetector.accept(block)) {
            BlockReading.Speech -> {
                state.speechSinceSearch = true
                state.quietBlocksSinceSearch = 0
            }
            BlockReading.Quiet -> if (state.hasSearched) state.quietBlocksSinceSearch++
            BlockReading.Pause -> {
                state.heard += trailingSilenceSymbols()
                onResult(resultFor(state.heard))
                state.hasSearched = true
                state.speechSinceSearch = false
                state.quietBlocksSinceSearch = 0
            }
        }
        state.isListening =
            !(state.hasSearched && state.quietBlocksSinceSearch >= SILENCE_END_BLOCKS)
    }

    // Everything one listening run remembers between blocks.
    private class ListeningState {
        val heard = mutableListOf<String>()
        val pauseDetector = SpeechPauseDetector()
        var blockCount = 0
        var speechSinceSearch = false
        var hasSearched = false
        var quietBlocksSinceSearch = 0
        var isListening = true
    }

    private fun resultFor(heardSymbols: List<String>): VoiceSearchResult {
        val matches = findAyahs(heardSymbols)
        val heardPhonemeCount = heardSymbols.count { it != BLANK_SYMBOL }
        val heardTooLittle = matches.isEmpty() && heardPhonemeCount < MINIMUM_HEARD_PHONEMES
        return VoiceSearchResult(matches, heardTooLittle)
    }

    // The streaming model emits the last word only after some silence. The silence goes to the
    // model alone; the microphone's audio is not changed.
    private fun trailingSilenceSymbols(): List<String> {
        val symbols = mutableListOf<String>()
        repeat(TRAILING_SILENCE_BLOCKS) {
            val silence = FloatArray(TrackerInput.SAMPLE_RATE_HZ / BLOCKS_PER_SECOND)
            symbols += source.accept(silence).map { it.symbol }
        }
        return symbols
    }

    companion object {
        /** The microphone gives 100 ms blocks. */
        private const val BLOCKS_PER_SECOND = 10
        private const val TRAILING_SILENCE_BLOCKS = BLOCKS_PER_SECOND

        /** Two and a half seconds of quiet after a search: long enough to be done reciting. */
        private const val SILENCE_END_BLOCKS = 25

        /** Mirrors the minimum in AyahFinder: with fewer phonemes it always finds nothing. */
        private const val MINIMUM_HEARD_PHONEMES = 8

        /** The model's blank token, which is not a sound. */
        private const val BLANK_SYMBOL = "<blank>"

        /** Fifteen seconds; longer adds little, because the first seconds decide the match. */
        const val MAXIMUM_BLOCKS = 15 * BLOCKS_PER_SECOND
    }
}
