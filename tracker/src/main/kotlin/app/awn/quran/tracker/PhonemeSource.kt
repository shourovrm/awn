package app.awn.quran.tracker

/** One phoneme the model heard. */
data class PhonemeEvent(val symbol: String, val onsetSeconds: Float, val probability: Float)

/** Turns audio into phonemes as it arrives. */
interface PhonemeSource : AutoCloseable {

    /** Feeds one block and returns only the phonemes that were not reported before. */
    fun accept(block: FloatArray): List<PhonemeEvent>

    /** Tells the source the audio has ended and returns the phonemes still pending. */
    fun finish(): List<PhonemeEvent>
}
