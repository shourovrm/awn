package app.awn.quran.feature.recite

/** What the recite bar shows: only a waiting message, or the full set of controls. */
enum class ReciteBarMode {
    /**
     * The speech model is not ready: one waiting word, a progress indicator and the close button.
     */
    Preparing,

    /** The model is ready: the status line and every control. */
    Controls,
}

/**
 * Maps the speech state to what the bar shows. Idle counts as preparing because the bar is on
 * screen from the moment recite starts, a little before the preparation is requested. A failed
 * preparation shows the controls: the microphone button tries again and the status line carries the
 * error.
 */
fun reciteBarMode(speechState: SpeechState): ReciteBarMode =
    when (speechState) {
        SpeechState.Idle,
        SpeechState.Preparing -> ReciteBarMode.Preparing
        SpeechState.Ready,
        SpeechState.Failed -> ReciteBarMode.Controls
    }
