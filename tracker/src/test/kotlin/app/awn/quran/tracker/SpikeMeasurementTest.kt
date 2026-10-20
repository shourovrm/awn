package app.awn.quran.tracker

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Measurements quoted in tools/spike_zipformer.md: onset granularity, chunk latency, madd gaps. */
class SpikeMeasurementTest {

    @Test
    fun onsetGranularityAndChunkLatency() {
        assumeTrue(TestPaths.fixturesAvailable())
        val input = WavTrackerInput(File(TestPaths.fixturesDirectory, "full_s1.wav"))
        val onsets = mutableListOf<Float>()
        val callMillis = mutableListOf<Double>()
        SherpaPhonemeSource(TestPaths.modelFile.path, TestPaths.tokensFile.path).use { source ->
            while (true) {
                val block = input.nextBlock() ?: break
                val started = System.nanoTime()
                onsets += source.accept(block).map { it.onsetSeconds }
                callMillis += (System.nanoTime() - started) / NANOS_PER_MILLI
            }
        }

        val gaps = onsets.zipWithNext { first, second -> second - first }
        val gapsOnFrameGrid = gaps.count { isMultipleOfFrame(it) }
        println(
            "onset gaps: ${gaps.size}, on the 0.04 s grid $gapsOnFrameGrid, min %.2f, median %.2f, max %.2f"
                .format(gaps.min(), gaps.sorted()[gaps.size / 2], gaps.max()),
        )

        // The first call after 0.48 s of audio, then every following call, decodes one chunk.
        val decodingCalls = callMillis.filter { it > DECODE_CALL_THRESHOLD_MILLIS }
        println(
            ("calls ${callMillis.size}, calls that decoded ${decodingCalls.size}, " +
                    "mean %.1f ms, max %.1f ms per 0.48 s chunk")
                .format(decodingCalls.average(), decodingCalls.max()),
        )
        println(
            "audio %.1f s, decode wall %.1f s"
                .format(input.durationSeconds, callMillis.sum() / MILLIS_PER_SECOND),
        )
        assertEquals(gaps.size, gapsOnFrameGrid)
        assertTrue(decodingCalls.isNotEmpty())
    }

    @Test
    fun maddOnsetGapsAtThreeSpeeds() {
        assumeTrue(TestPaths.fixturesAvailable())
        for (name in
            listOf(
                "stretch_ayah_001001_t0.8.wav",
                "ayah/001001.wav",
                "stretch_ayah_001001_t1.2.wav",
            )) {
            val events = RecordingDecoder.decode(File(TestPaths.fixturesDirectory, name)).allEvents
            println("== $name")
            for (index in 0 until events.size - 1) {
                val symbol = events[index].symbol
                val gap = events[index + 1].onsetSeconds - events[index].onsetSeconds
                val kind = if (symbol.any { it in "اۥۦ" }) "long" else "short"
                println(
                    "   $symbol $kind onset %.2f gap %.2f".format(events[index].onsetSeconds, gap),
                )
            }
        }
    }

    private fun isMultipleOfFrame(seconds: Float): Boolean {
        val frames = seconds / FRAME_SECONDS
        return kotlin.math.abs(frames - Math.round(frames)) < FRAME_TOLERANCE
    }

    private companion object {
        const val FRAME_SECONDS = 0.04f
        const val FRAME_TOLERANCE = 0.05f
        const val NANOS_PER_MILLI = 1_000_000.0
        const val MILLIS_PER_SECOND = 1000.0
        const val DECODE_CALL_THRESHOLD_MILLIS = 5.0
    }
}
