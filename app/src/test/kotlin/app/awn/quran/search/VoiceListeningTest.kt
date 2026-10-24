package app.awn.quran.search

import app.awn.quran.tracker.AyahMatch
import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.TrackerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceListeningTest {

    private val firstAyah = AyahMatch(surah = 1, ayah = 1, score = 40)
    private val secondAyah = AyahMatch(surah = 2, ayah = 255, score = 40)

    @Test
    fun searchesOnceASecondWhileListening() {
        val searches = mutableListOf<Int>()
        val listening =
            VoiceListening(BlockInput(blockCount = 35), OneSymbolPerBlock()) { heard ->
                searches += heard.size
                emptyList()
            }

        listening.run {}

        // After 1, 2 and 3 s of audio, then once more with everything heard.
        assertEquals(listOf(10, 20, 30), searches.dropLast(1))
        assertTrue(searches.last() > 35)
    }

    @Test
    fun theFinalSearchIncludesWhatTheModelDecodesAfterTheLastBlock() {
        val source = OneSymbolPerBlock(symbolsOnFinish = listOf("x", "y"))
        var lastHeard = emptyList<String>()
        VoiceListening(BlockInput(blockCount = 5), source) { heard ->
                lastHeard = heard
                emptyList()
            }
            .run {}

        assertEquals(listOf("x", "y"), lastHeard.takeLast(2))
    }

    @Test
    fun stopsOnceTheSameAyahLedThreeSearchesInARowAndFiveSecondsWereHeard() {
        val input = BlockInput(blockCount = 1_000)
        val listening = VoiceListening(input, OneSymbolPerBlock()) { listOf(firstAyah) }

        val matches = listening.run {}

        assertEquals(listOf(firstAyah), matches)
        // The searches at 3, 4 and 5 s lead with the same ayah, and 5 s is the shortest listen.
        assertEquals(50, input.blocksGiven)
    }

    @Test
    fun keepsListeningWhileTheLeadingAyahChanges() {
        val input = BlockInput(blockCount = 1_000)
        var searchCount = 0
        val listening =
            VoiceListening(input, OneSymbolPerBlock()) {
                searchCount++
                if (searchCount % 2 == 0) listOf(firstAyah) else listOf(secondAyah)
            }

        listening.run {}

        assertEquals(VoiceListening.MAXIMUM_BLOCKS, input.blocksGiven)
    }

    @Test
    fun reportsEachSearchAsItHappens() {
        val reported = mutableListOf<List<AyahMatch>>()
        VoiceListening(BlockInput(blockCount = 20), OneSymbolPerBlock()) { listOf(firstAyah) }
            .run { matches -> reported += matches }

        assertEquals(3, reported.size)
    }

    @Test
    fun endsWhenTheMicrophoneStops() {
        val input = BlockInput(blockCount = 12)
        VoiceListening(input, OneSymbolPerBlock()) { emptyList() }.run {}

        assertEquals(12, input.blocksGiven)
    }

    private class BlockInput(private val blockCount: Int) : TrackerInput {
        var blocksGiven = 0
            private set

        override fun nextBlock(): FloatArray? {
            if (blocksGiven == blockCount) return null
            blocksGiven++
            return FloatArray(TrackerInput.SAMPLE_RATE_HZ / BLOCKS_PER_SECOND)
        }
    }

    // Reports one symbol for every block it is given, including the silence after the recording.
    private class OneSymbolPerBlock(private val symbolsOnFinish: List<String> = emptyList()) :
        PhonemeSource {
        override fun accept(block: FloatArray): List<PhonemeEvent> =
            listOf(PhonemeEvent("a", onsetSeconds = 0f, probability = 1f))

        override fun finish(): List<PhonemeEvent> = symbolsOnFinish.map {
            PhonemeEvent(it, onsetSeconds = 0f, probability = 1f)
        }

        override fun close() = Unit
    }

    private companion object {
        const val BLOCKS_PER_SECOND = 10
    }
}
