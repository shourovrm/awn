package app.awn.quran.tracker

/** A source of audio for the tracker: the microphone on the phone, a WAV file in tests. */
interface TrackerInput {

    /**
     * Returns the next block of 16 kHz mono PCM samples scaled to -1.0..1.0, waiting until one is
     * ready, or null when the input has ended. Every implementation must deliver 16 kHz; a
     * different rate makes the model's output wrong without any error.
     */
    fun nextBlock(): FloatArray?

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}
