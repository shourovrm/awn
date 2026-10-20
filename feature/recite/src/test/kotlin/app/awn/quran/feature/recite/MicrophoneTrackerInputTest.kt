package app.awn.quran.feature.recite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MicrophoneTrackerInputTest {

    @Test
    fun aBlockIsOneHundredMillisecondsAtSixteenKilohertz() {
        assertEquals(1_600, MicrophoneTrackerInput.SAMPLES_PER_BLOCK)
    }

    @Test
    fun samplesAreScaledIntoMinusOneToOne() {
        val floats =
            MicrophoneTrackerInput.samplesToFloats(shortArrayOf(0, 16384, -16384, Short.MIN_VALUE))

        assertArrayEquals(floatArrayOf(0f, 0.5f, -0.5f, -1f), floats, 0f)
    }
}
