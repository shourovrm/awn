package app.awn.quran.tracker

/**
 * Follows the user's recitation against a reference text. Words are identified by their index in
 * the reference passed to the implementation, so this module does not depend on the Android-only
 * word key; the app maps between the two.
 */
interface Tracker {

    /** Feeds newly heard phonemes. */
    fun onPhonemes(events: List<PhonemeEvent>)

    fun stateOfWord(referenceWordIndex: Int): WordState

    /** Forgets everything heard so far. */
    fun reset()
}
