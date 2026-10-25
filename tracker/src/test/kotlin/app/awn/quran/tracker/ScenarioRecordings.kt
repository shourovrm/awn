package app.awn.quran.tracker

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds recordings of reciting habits from the Husary ayah files: an ayah said twice, an ayah
 * restarted, going back, jumping ahead, a noise between ayahs. Pieces are joined with the same 0.4
 * s pause as the fetchFixtures task, and each recording is written once to fixtures/scenario/ so
 * [RecordingDecoder] can cache what the model hears in it.
 */
object ScenarioRecordings {
    private const val PAUSE_SECONDS = 0.4f
    private const val SHORT_SCALE = 32767f
    private const val HEADER_BYTES = 44
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = 2

    fun ayah(surah: Int, ayah: Int): FloatArray =
        readSamples(File(TestPaths.fixturesDirectory, "ayah/%03d%03d.wav".format(surah, ayah)))

    /** The first [share] of the ayah's audio, as a reciter who stops and starts the ayah again. */
    fun startOfAyah(surah: Int, ayah: Int, share: Float): FloatArray {
        val samples = ayah(surah, ayah)
        return samples.copyOfRange(0, (samples.size * share).toInt())
    }

    /** The ayah without its first [share]: what is left after its start was replaced. */
    fun restOfAyah(surah: Int, ayah: Int, share: Float): FloatArray {
        val samples = ayah(surah, ayah)
        return samples.copyOfRange((samples.size * share).toInt(), samples.size)
    }

    /** The start of another ayah, as spliced into the splice fixtures. */
    fun foreignWords(surah: Int, ayah: Int): FloatArray =
        readSamples(File(TestPaths.fixturesDirectory, "word_%03d%03d.wav".format(surah, ayah)))

    /** Writes the pieces joined by pauses to fixtures/scenario/[name].wav, once. */
    fun build(name: String, pieces: List<FloatArray>): File {
        val file = File(TestPaths.fixturesDirectory, "scenario/$name.wav")
        if (file.isFile) return file
        val pause = FloatArray((PAUSE_SECONDS * TrackerInput.SAMPLE_RATE_HZ).toInt())
        val joined = pieces.flatMapIndexed { index, piece ->
            if (index == 0) listOf(piece) else listOf(pause, piece)
        }
        file.parentFile.mkdirs()
        writeWav(file, joined)
        return file
    }

    private fun readSamples(file: File): FloatArray {
        val input = WavTrackerInput(file)
        val blocks = mutableListOf<FloatArray>()
        while (true) blocks += input.nextBlock() ?: break
        return concatenate(blocks)
    }

    private fun concatenate(parts: List<FloatArray>): FloatArray {
        val joined = FloatArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(joined, offset)
            offset += part.size
        }
        return joined
    }

    // 16 kHz mono 16-bit PCM, the only format WavTrackerInput accepts.
    private fun writeWav(file: File, parts: List<FloatArray>) {
        val samples = concatenate(parts)
        val dataBytes = samples.size * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(HEADER_BYTES - 8 + dataBytes)
        buffer.put("WAVE".toByteArray()).put("fmt ".toByteArray()).putInt(16)
        buffer.putShort(1).putShort(1).putInt(TrackerInput.SAMPLE_RATE_HZ)
        buffer.putInt(TrackerInput.SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)
        buffer.putShort(BYTES_PER_SAMPLE.toShort()).putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray()).putInt(dataBytes)
        for (sample in samples) {
            buffer.putShort((sample.coerceIn(-1f, 1f) * SHORT_SCALE).toInt().toShort())
        }
        file.writeBytes(buffer.array())
    }
}
