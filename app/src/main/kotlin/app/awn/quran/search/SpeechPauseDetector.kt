package app.awn.quran.search

import kotlin.math.sqrt

/** How one 100 ms microphone block reads: speech, quiet, or the block that ends a pause. */
enum class BlockReading {
    Speech,
    Quiet,

    /** The quiet block that completes a run of [PAUSE_BLOCKS] quiet blocks after speech. */
    Pause,
}

/**
 * Splits the microphone into utterances: a pause is reported once speech was heard and then
 * [PAUSE_BLOCKS] quiet blocks follow. Each block is judged on its root mean square (RMS), the
 * loudness of its samples, against a noise floor learned from the first blocks.
 */
class SpeechPauseDetector {

    private val learningRmsValues = mutableListOf<Float>()
    private var noiseFloorRms: Float? = null
    private var quietBlockRun = 0
    private var speechSeenInUtterance = false

    fun accept(block: FloatArray): BlockReading {
        val rms = rootMeanSquare(block)
        val noiseFloor = noiseFloorRms ?: return learnNoiseFloor(rms)
        return if (rms > speechThreshold(noiseFloor)) speechReading() else quietReading()
    }

    private fun learnNoiseFloor(rms: Float): BlockReading {
        learningRmsValues += rms
        // The quietest learning block sets the floor, so a speaker who starts at once does not
        // raise the floor to speech level and hide every later pause.
        if (learningRmsValues.size == NOISE_FLOOR_BLOCKS) noiseFloorRms = learningRmsValues.min()
        return BlockReading.Quiet
    }

    private fun speechReading(): BlockReading {
        speechSeenInUtterance = true
        quietBlockRun = 0
        return BlockReading.Speech
    }

    private fun quietReading(): BlockReading {
        quietBlockRun++
        if (speechSeenInUtterance && quietBlockRun == PAUSE_BLOCKS) {
            // Re-arms the detector: only speech after this pause starts the next utterance.
            speechSeenInUtterance = false
            return BlockReading.Pause
        }
        return BlockReading.Quiet
    }

    private fun speechThreshold(noiseFloor: Float): Float =
        maxOf(noiseFloor * SPEECH_ABOVE_NOISE_FACTOR, MINIMUM_SPEECH_RMS)

    private fun rootMeanSquare(block: FloatArray): Float {
        if (block.isEmpty()) return 0f
        var sumOfSquares = 0.0
        for (sample in block) sumOfSquares += sample.toDouble() * sample
        return sqrt(sumOfSquares / block.size).toFloat()
    }

    private companion object {
        /** Three 100 ms blocks (0.3 s): enough to see the room, short before a reciter starts. */
        const val NOISE_FLOOR_BLOCKS = 3

        /** Speech sits about 10 dB (a factor of about 3 in RMS) above room noise. */
        const val SPEECH_ABOVE_NOISE_FACTOR = 3f

        /**
         * About -40 dBFS. A stopped or silent microphone reads near zero, so a ratio alone would
         * call faint hiss speech; this keeps hiss under the threshold.
         */
        const val MINIMUM_SPEECH_RMS = 0.01f

        /** Four blocks (0.4 s): shorter gaps between words would split one recitation in two. */
        const val PAUSE_BLOCKS = 4
    }
}
