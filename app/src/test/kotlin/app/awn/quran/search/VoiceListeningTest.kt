package app.awn.quran.search

import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.TrackerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceListeningTest {

    // Quiet blocks first, so the detector learns the noise floor before any speech.
    private val lead = "qqq"

    @Test
    fun aPauseSearchesAllAudioHeardSoFarOnce() {
        val pattern = lead + speech(10) + quiet(4) + speech(10) + quiet(4) + quiet(25)
        val searchedSizes = mutableListOf<Int>()
        VoiceListening(ScriptedMicrophone(pattern), SpeechSource()) { heard ->
                searchedSizes += heard.size
                emptyList()
            }
            .run {}

        // Each search sees the speech of every earlier part, not only the newest part.
        assertEquals(listOf(10, 20), searchedSizes)
    }

    @Test
    fun speechWithoutAPauseIsSearchedOnlyWhenListeningEnds() {
        val microphone = ScriptedMicrophone(lead + speech(30))
        val blocksGivenAtSearch = mutableListOf<Int>()
        VoiceListening(microphone, SpeechSource()) {
                blocksGivenAtSearch += microphone.blocksGiven
                emptyList()
            }
            .run {}

        // The one search happens after the microphone stopped, at the end of all 33 blocks.
        assertEquals(listOf(lead.length + 30), blocksGivenAtSearch)
    }

    @Test
    fun quietAfterASearchEndsListeningAfterTwoAndAHalfSeconds() {
        val microphone = ScriptedMicrophone(lead + speech(10) + quiet(4) + quiet(100))
        VoiceListening(microphone, SpeechSource()) { emptyList() }.run {}

        // The pause at block 17 is the search; 25 quiet blocks after it end listening.
        assertEquals(17 + 25, microphone.blocksGiven)
    }

    @Test
    fun listeningEndsAtTheMaximumBlocks() {
        val microphone = ScriptedMicrophone(lead + speech(VoiceListening.MAXIMUM_BLOCKS))
        VoiceListening(microphone, SpeechSource()) { emptyList() }.run {}

        assertEquals(VoiceListening.MAXIMUM_BLOCKS, microphone.blocksGiven)
    }

    @Test
    fun theFinalSearchIncludesWhatTheModelDecodesAfterTheLastBlock() {
        val source = SpeechSource(finishSymbols = listOf("x", "y"))
        val searchedSymbols = mutableListOf<List<String>>()
        VoiceListening(ScriptedMicrophone(lead + speech(5)), source) { heard ->
                searchedSymbols += heard
                emptyList()
            }
            .run {}

        assertEquals(listOf("x", "y"), searchedSymbols.last().takeLast(2))
    }

    @Test
    fun aRecitationTooShortForTheFinderPublishesTooLittleHeard() {
        val results = mutableListOf<VoiceSearchResult>()
        val microphone = ScriptedMicrophone(lead + speech(3) + quiet(4) + quiet(25))
        VoiceListening(microphone, SpeechSource()) { emptyList() }
            .run { result -> results += result }

        assertTrue(results.single().heardTooLittle)
        assertTrue(results.single().matches.isEmpty())
    }

    @Test
    fun stoppingBeforeAnySpeechPublishesTooLittleHeard() {
        val results = mutableListOf<VoiceSearchResult>()
        VoiceListening(ScriptedMicrophone(lead), SpeechSource()) { emptyList() }
            .run { result -> results += result }

        assertTrue(results.single().heardTooLittle)
    }

    private fun speech(blockCount: Int): String = "s".repeat(blockCount)

    private fun quiet(blockCount: Int): String = "q".repeat(blockCount)

    // Reads one block per letter of [pattern]: 's' is loud, 'q' silent. Then it stops.
    private class ScriptedMicrophone(private val pattern: String) : TrackerInput {
        var blocksGiven = 0
            private set

        override fun nextBlock(): FloatArray? {
            if (blocksGiven == pattern.length) return null
            val isSpeech = pattern[blocksGiven] == 's'
            blocksGiven++
            return FloatArray(TrackerInput.SAMPLE_RATE_HZ / BLOCKS_PER_SECOND) {
                if (isSpeech) LOUD else SILENT
            }
        }
    }

    // Hears one phoneme per loud block; a silent block gives the model nothing.
    private class SpeechSource(private val finishSymbols: List<String> = emptyList()) :
        PhonemeSource {
        override fun accept(block: FloatArray): List<PhonemeEvent> =
            if (block.any { it != SILENT }) {
                listOf(PhonemeEvent("a", onsetSeconds = 0f, probability = 1f))
            } else {
                emptyList()
            }

        override fun finish(): List<PhonemeEvent> = finishSymbols.map {
            PhonemeEvent(it, onsetSeconds = 0f, probability = 1f)
        }

        override fun close() = Unit
    }

    private companion object {
        const val BLOCKS_PER_SECOND = 10
        const val LOUD = 0.5f
        const val SILENT = 0f
    }
}
