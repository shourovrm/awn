package app.awn.quran.feature.recite

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.awn.quran.tracker.TrackerInput

/**
 * Reads the microphone as 16 kHz mono 16-bit PCM and hands it out in 100 ms blocks scaled to
 * -1.0..1.0. `VOICE_RECOGNITION` is used because it switches off the effects that change the speech
 * spectrum on most phones.
 *
 * [nextBlock] blocks until a full block was recorded, so the caller must run it on a thread of its
 * own; [RecitationSession] does. [close] may be called from any thread and makes a waiting
 * [nextBlock] return null.
 */
class MicrophoneTrackerInput(private val context: Context) : TrackerInput, AutoCloseable {

    private val lock = Any()
    private var record: AudioRecord? = null
    @Volatile private var closed = false

    // Three exits: no microphone, the read ended, or a full block; each is a plain guard.
    @Suppress("ReturnCount")
    override fun nextBlock(): FloatArray? {
        val activeRecord = openRecord() ?: return null
        val samples = ShortArray(SAMPLES_PER_BLOCK)
        var filled = 0
        while (filled < SAMPLES_PER_BLOCK) {
            val readCount = activeRecord.read(samples, filled, SAMPLES_PER_BLOCK - filled)
            if (readCount <= 0 || closed) {
                releaseRecord()
                return null
            }
            filled += readCount
        }
        return samplesToFloats(samples)
    }

    override fun close() {
        closed = true
        synchronized(lock) {
            // stop() makes a read that is waiting on another thread return, which then releases.
            record?.stop()
        }
    }

    private fun openRecord(): AudioRecord? =
        synchronized(lock) {
            if (closed) {
                releaseRecord()
                return null
            }
            record?.let {
                return it
            }
            check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == GRANTED) {
                "The microphone permission was not granted."
            }
            val minimumBufferBytes =
                AudioRecord.getMinBufferSize(
                    TrackerInput.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            check(minimumBufferBytes > 0) { "This device cannot record 16 kHz mono audio." }
            val created =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    TrackerInput.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimumBufferBytes, BYTES_PER_BLOCK * BLOCKS_OF_BUFFER),
                )
            check(created.state == AudioRecord.STATE_INITIALIZED) {
                created.release()
                "The microphone could not be opened."
            }
            created.startRecording()
            record = created
            created
        }

    private fun releaseRecord() {
        synchronized(lock) {
            record?.release()
            record = null
            closed = true
        }
    }

    companion object {
        private const val GRANTED = PackageManager.PERMISSION_GRANTED
        const val BLOCK_MILLISECONDS = 100
        const val SAMPLES_PER_BLOCK = TrackerInput.SAMPLE_RATE_HZ * BLOCK_MILLISECONDS / 1000
        private const val BYTES_PER_SAMPLE = 2
        private const val BYTES_PER_BLOCK = SAMPLES_PER_BLOCK * BYTES_PER_SAMPLE

        /** Room for this many blocks so a slow consumer does not lose audio. */
        private const val BLOCKS_OF_BUFFER = 8

        private const val SHORT_RANGE = 32768f

        /** Scales signed 16-bit samples to -1.0..1.0. */
        internal fun samplesToFloats(samples: ShortArray): FloatArray =
            FloatArray(samples.size) { index -> samples[index] / SHORT_RANGE }
    }
}
