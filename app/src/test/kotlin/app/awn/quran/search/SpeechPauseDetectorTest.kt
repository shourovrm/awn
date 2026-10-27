package app.awn.quran.search

import app.awn.quran.tracker.TrackerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPauseDetectorTest {

    @Test
    fun silenceAloneNeverReportsAPause() {
        val readings = readingsOf("q".repeat(60))

        assertTrue(readings.none { it == BlockReading.Pause })
    }

    @Test
    fun threeQuietBlocksAfterSpeechReportNoPause() {
        val readings = readingsOf("qqq" + "s" + "qqq")

        assertTrue(readings.none { it == BlockReading.Pause })
    }

    @Test
    fun fourQuietBlocksAfterSpeechReportAPauseOnce() {
        // Blocks 0 to 2 teach the noise floor; the fourth quiet block after the speech is index 7.
        val readings = readingsOf("qqq" + "s" + "qqqqq")

        assertEquals(1, readings.count { it == BlockReading.Pause })
        assertEquals(7, readings.indexOf(BlockReading.Pause))
    }

    @Test
    fun speechAfterAPauseStartsANewUtteranceThatReportsAgain() {
        val readings = readingsOf("qqq" + "s" + "qqqq" + "ss" + "qqqq")

        // The second pause is the fourth quiet block after the second speech, at index 13.
        assertEquals(listOf(7, 13), readings.indices.filter { readings[it] == BlockReading.Pause })
    }

    @Test
    fun constantLowNoiseCountsAsQuiet() {
        val detector = SpeechPauseDetector()

        val readings = List(40) { detector.accept(blockAt(NOISE_LEVEL)) }

        assertTrue(readings.none { it == BlockReading.Speech })
    }

    @Test
    fun speechNeedsThreeTimesTheLearnedNoiseFloor() {
        val detector = SpeechPauseDetector()
        repeat(3) { detector.accept(blockAt(FLOOR_LEVEL)) }

        // The threshold is 3 x 0.05 = 0.15: a block at 0.1 is still noise, one at 0.2 is speech.
        assertEquals(BlockReading.Quiet, detector.accept(blockAt(0.1f)))
        assertEquals(BlockReading.Speech, detector.accept(blockAt(0.2f)))
    }

    @Test
    fun aLoudFirstBlockDoesNotRaiseTheNoiseFloor() {
        val detector = SpeechPauseDetector()
        detector.accept(blockAt(LOUD_LEVEL))
        detector.accept(blockAt(NOISE_LEVEL))
        detector.accept(blockAt(NOISE_LEVEL))

        assertEquals(BlockReading.Speech, detector.accept(blockAt(LOUD_LEVEL)))
    }

    // 's' is a speech block, 'q' a quiet one.
    private fun readingsOf(pattern: String): List<BlockReading> {
        val detector = SpeechPauseDetector()
        return pattern.map { letter ->
            detector.accept(blockAt(if (letter == 's') LOUD_LEVEL else 0f))
        }
    }

    private fun blockAt(level: Float): FloatArray =
        FloatArray(TrackerInput.SAMPLE_RATE_HZ / BLOCKS_PER_SECOND) { level }

    private companion object {
        const val BLOCKS_PER_SECOND = 10
        const val LOUD_LEVEL = 0.5f
        const val FLOOR_LEVEL = 0.05f
        const val NOISE_LEVEL = 0.005f
    }
}
