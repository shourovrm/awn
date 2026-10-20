package app.awn.quran.tracker

import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic room sounds for the noise tests. Every generator is seeded so a failing run can be
 * repeated. Levels are root-mean-square amplitudes on the -1..1 scale of [TrackerInput]; a quiet
 * room is about 0.002, a fan or traffic 0.01 to 0.03, and speech held close to the microphone 0.05
 * to 0.2.
 */
object NoiseSupport {
    private const val RATE = TrackerInput.SAMPLE_RATE_HZ
    private const val SEED = 20261021
    private const val SYLLABLE_RATE_HERTZ = 4.0

    fun silence(seconds: Float): FloatArray = FloatArray((seconds * RATE).toInt())

    fun white(seconds: Float, rms: Float): FloatArray {
        val random = Random(SEED)
        return scaleToRms(FloatArray((seconds * RATE).toInt()) { random.nextFloat() * 2 - 1 }, rms)
    }

    /** Pink noise (equal energy per octave) from Paul Kellet's three-pole filter. */
    fun pink(seconds: Float, rms: Float): FloatArray {
        val random = Random(SEED + 1)
        var b0 = 0f
        var b1 = 0f
        var b2 = 0f
        val raw =
            FloatArray((seconds * RATE).toInt()) {
                val white = random.nextFloat() * 2 - 1
                b0 = 0.99765f * b0 + white * 0.0990460f
                b1 = 0.96300f * b1 + white * 0.2965164f
                b2 = 0.57000f * b2 + white * 1.0526913f
                b0 + b1 + b2 + white * 0.1848f
            }
        return scaleToRms(raw, rms)
    }

    /** Brown noise (a fan or traffic rumble): the running sum of white noise, leaky. */
    fun brown(seconds: Float, rms: Float): FloatArray {
        val random = Random(SEED + 2)
        var state = 0f
        val raw =
            FloatArray((seconds * RATE).toInt()) {
                state = 0.998f * state + (random.nextFloat() * 2 - 1) * 0.05f
                state
            }
        return scaleToRms(raw, rms)
    }

    /** Mains hum: 50 Hz with its next two harmonics. */
    fun hum(seconds: Float, rms: Float): FloatArray {
        val raw =
            FloatArray((seconds * RATE).toInt()) { index ->
                val time = index.toDouble() / RATE
                (sin(2 * PI * 50 * time) +
                        0.5 * sin(2 * PI * 100 * time) +
                        0.3 * sin(2 * PI * 150 * time))
                    .toFloat()
            }
        return scaleToRms(raw, rms)
    }

    /**
     * Pink noise switched on and off four times a second, the loudness pattern of syllables, which
     * is the sound most likely to fool a speech model that has no words to hear.
     */
    fun syllableNoise(seconds: Float, rms: Float): FloatArray {
        val base = pink(seconds, 1f)
        val raw =
            FloatArray(base.size) { index ->
                val time = index.toDouble() / RATE
                val envelope = 0.5 + 0.5 * sin(2 * PI * SYLLABLE_RATE_HERTZ * time)
                (base[index] * envelope).toFloat()
            }
        return scaleToRms(raw, rms)
    }

    /** Short decaying clicks about every 0.7 s: a pen, a door, a keyboard. */
    fun clicks(seconds: Float, peak: Float): FloatArray {
        val random = Random(SEED + 3)
        val samples = FloatArray((seconds * RATE).toInt())
        var position = RATE / 2
        while (position < samples.size) {
            val length = RATE / 100
            for (offset in 0 until length) {
                if (position + offset >= samples.size) break
                val decay = 1f - offset.toFloat() / length
                samples[position + offset] = peak * decay * (random.nextFloat() * 2 - 1)
            }
            position += (RATE * (0.4f + random.nextFloat() * 0.6f)).toInt()
        }
        return samples
    }

    /** Reads a 16 kHz mono WAV from the noise folder, or null when the file is not there. */
    fun recorded(name: String): FloatArray? {
        val file = File(TestPaths.fixturesDirectory, "noise/$name")
        if (!file.isFile) return null
        val input = WavTrackerInput(file)
        val blocks = mutableListOf<FloatArray>()
        while (true) blocks += input.nextBlock() ?: break
        return blocks.toTypedArray().let { parts ->
            FloatArray(parts.sumOf { it.size }).also { joined ->
                var offset = 0
                for (part in parts) {
                    part.copyInto(joined, offset)
                    offset += part.size
                }
            }
        }
    }

    fun scaleToRms(samples: FloatArray, targetRms: Float): FloatArray {
        val currentRms = rms(samples)
        if (currentRms == 0f) return samples
        val gain = targetRms / currentRms
        return FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
    }

    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) sum += sample.toDouble() * sample
        return kotlin.math.sqrt(sum / samples.size).toFloat()
    }

    /** Cuts [samples] into the 100 ms blocks the microphone input delivers. */
    fun toBlocks(samples: FloatArray): List<FloatArray> {
        val blockSize = RATE / 10
        return (samples.indices step blockSize).map {
            samples.copyOfRange(it, minOf(it + blockSize, samples.size))
        }
    }
}
