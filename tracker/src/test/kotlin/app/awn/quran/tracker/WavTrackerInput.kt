package app.awn.quran.tracker

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Delivers a 16 kHz mono 16-bit PCM WAV file as 100 ms blocks, the way the microphone input will.
 * It refuses any other format, because the model gives wrong output without an error at a wrong
 * sample rate.
 */
class WavTrackerInput(file: File) : TrackerInput {

    private val samples: FloatArray = readSamples(file)
    private var nextSampleIndex = 0

    val durationSeconds: Float
        get() = samples.size.toFloat() / TrackerInput.SAMPLE_RATE_HZ

    override fun nextBlock(): FloatArray? {
        if (nextSampleIndex >= samples.size) return null
        val endIndex = minOf(nextSampleIndex + BLOCK_SAMPLES, samples.size)
        val block = samples.copyOfRange(nextSampleIndex, endIndex)
        nextSampleIndex = endIndex
        return block
    }

    private fun readSamples(file: File): FloatArray {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.getInt(0) == RIFF_TAG && bytes.getInt(WAVE_TAG_OFFSET) == WAVE_TAG) {
            "${file.name} is not a RIFF/WAVE file"
        }
        var chunkOffset = FIRST_CHUNK_OFFSET
        var formatChecked = false
        while (chunkOffset + CHUNK_HEADER_BYTES <= bytes.limit()) {
            val chunkTag = bytes.getInt(chunkOffset)
            val declaredSize = bytes.getInt(chunkOffset + CHUNK_TAG_BYTES)
            val bodyOffset = chunkOffset + CHUNK_HEADER_BYTES
            if (chunkTag == FORMAT_TAG) {
                checkFormat(file, bytes, bodyOffset)
                formatChecked = true
            }
            if (chunkTag == DATA_TAG) {
                check(formatChecked) { "${file.name} has audio before its fmt chunk" }
                // A streamed file can declare a size larger than what was written.
                val availableBytes = bytes.limit() - bodyOffset
                val sizeBytes =
                    if (declaredSize in 0..availableBytes) declaredSize else availableBytes
                return decodePcm(bytes, bodyOffset, sizeBytes)
            }
            chunkOffset = bodyOffset + declaredSize + (declaredSize and 1)
        }
        error("${file.name} has no data chunk")
    }

    private fun checkFormat(file: File, bytes: ByteBuffer, bodyOffset: Int) {
        val encoding = bytes.getShort(bodyOffset).toInt()
        val channelCount = bytes.getShort(bodyOffset + CHANNELS_OFFSET).toInt()
        val sampleRate = bytes.getInt(bodyOffset + SAMPLE_RATE_OFFSET)
        val bitsPerSample = bytes.getShort(bodyOffset + BITS_OFFSET).toInt()
        require(
            encoding == PCM_ENCODING &&
                channelCount == 1 &&
                sampleRate == TrackerInput.SAMPLE_RATE_HZ &&
                bitsPerSample == BITS_PER_SAMPLE,
        ) {
            "${file.name} must be 16 kHz mono 16-bit PCM, found encoding $encoding, " +
                "$channelCount channels, $sampleRate Hz, $bitsPerSample bits"
        }
    }

    private fun decodePcm(bytes: ByteBuffer, offset: Int, sizeBytes: Int): FloatArray {
        val sampleCount = sizeBytes / BYTES_PER_SAMPLE
        return FloatArray(sampleCount) { index ->
            bytes.getShort(offset + index * BYTES_PER_SAMPLE) / SHORT_SCALE
        }
    }

    private companion object {
        const val BLOCK_SAMPLES = TrackerInput.SAMPLE_RATE_HZ / 10
        const val RIFF_TAG = 0x46464952
        const val WAVE_TAG = 0x45564157
        const val FORMAT_TAG = 0x20746d66
        const val DATA_TAG = 0x61746164
        const val WAVE_TAG_OFFSET = 8
        const val FIRST_CHUNK_OFFSET = 12
        const val CHUNK_TAG_BYTES = 4
        const val CHUNK_HEADER_BYTES = 8
        const val CHANNELS_OFFSET = 2
        const val SAMPLE_RATE_OFFSET = 4
        const val BITS_OFFSET = 14
        const val PCM_ENCODING = 1
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = 2
        const val SHORT_SCALE = 32768f
    }
}
