package app.awn.quran.tracker

/**
 * Identifies a word by surah, ayah and word position, all 1-based. It repeats the shape of the
 * `WordKey` in :core:data because this module must not depend on Android modules; the app maps
 * between the two.
 */
data class WordKeyLike(val surah: Int, val ayah: Int, val wordPosition: Int)

/**
 * A lengthened vowel in a word: [letter] is the long-vowel phoneme, [rule] the madd type
 * ("natural", "monfasel", "mottasel", "mottasel_waqf", "aared" or "lazim"), [harakat] the number of
 * counts the recitation style asks for, and [phonemeIndex] the position of the run's first phoneme
 * in [ReferenceWord.phonemes].
 */
data class MaddAnnotation(
    val letter: String,
    val rule: String,
    val harakat: Int,
    val phonemeIndex: Int,
)

/**
 * One word of the text the user should recite, with the phonemes the model should hear for it and
 * the lengthened vowels it holds.
 */
data class ReferenceWord(
    val key: WordKeyLike,
    val phonemes: List<String>,
    val madds: List<MaddAnnotation> = emptyList(),
)

enum class DurationVerdict {
    Ok,
    Underheld,
    Overheld,
}

/**
 * How long the user held a lengthened vowel of the word at [wordIndex], measured as the time from
 * the vowel's token onset to the next token's onset, against the time the rule asks for.
 */
data class DurationCheck(
    val wordIndex: Int,
    val letter: String,
    val rule: String,
    val measuredSeconds: Float,
    val expectedSeconds: Float,
    val verdict: DurationVerdict,
    /** When the vowel's token started, in seconds of audio; 0 when unknown. */
    val onsetSeconds: Float = 0f,
)

/**
 * How strict the tracker is about a word being said: each level is one row of thresholds (see
 * docs/decisions/tracker.md). Strict allows fewer differences before a word is said; Lenient more.
 */
enum class Strictness {
    Lenient,
    Normal,
    Strict;

    companion object {
        private const val LENIENT_BELOW_SPEED = 1f / 3
        private const val STRICT_ABOVE_SPEED = 2f / 3

        /**
         * The level for the speed value the settings store, 0 (patient) to 1 (eager): a patient
         * reader wants forgiveness, an eager one wants every slip flagged.
         */
        fun fromSpeed(speed: Float): Strictness =
            when {
                speed < LENIENT_BELOW_SPEED -> Lenient
                speed > STRICT_ABOVE_SPEED -> Strict
                else -> Normal
            }
    }
}

data class TrackerConfig(val strictness: Strictness = Strictness.Normal) {
    companion object {
        /** The config for the speed the settings and the speed popup still store. */
        fun fromSpeed(speed: Float): TrackerConfig = TrackerConfig(Strictness.fromSpeed(speed))
    }
}

/** A change the tracker reports; indexes refer to positions in the list given to [Tracker.load]. */
sealed interface TrackerEvent {
    data class WordChanged(val index: Int, val state: WordState) : TrackerEvent

    /** The user left out the word at [index]. */
    data class Skipped(val index: Int) : TrackerEvent

    /** The user said extra material after the word at [afterIndex]. */
    data class Inserted(val afterIndex: Int) : TrackerEvent

    /**
     * The user said [heard] where the reference expects [expected], at position [phonemeIndex]
     * (0-based) in the phonemes of the word at [wordIndex]. Reported once the user has moved on to
     * a later word. The model leans towards the correct rule, so many real errors never show.
     */
    data class Substitution(
        val wordIndex: Int,
        val phonemeIndex: Int,
        val heard: String,
        val expected: String,
    ) : TrackerEvent

    /** The user held a lengthened vowel for the time in [check]; reported once per vowel. */
    data class Duration(val check: DurationCheck) : TrackerEvent

    /** The tracker re-synchronised to the word at [index] after losing the place. */
    data class Anchored(val index: Int) : TrackerEvent

    /**
     * The user went back and said the words from [firstWord] again, having reached [lastWord].
     * Every mark stays; the word being said now moves back to the word after [firstWord].
     */
    data class Repeated(val firstWord: Int, val lastWord: Int) : TrackerEvent

    /** The word at [index] was wrong or left out and has now been said right; it is Confirmed. */
    data class Corrected(val index: Int) : TrackerEvent

    /**
     * The user went on at [toWord], far past [fromWord], the word they were expected to say. The
     * words in between are [WordState.Unread]: not recited, and not a mistake.
     */
    data class JumpedAhead(val fromWord: Int, val toWord: Int) : TrackerEvent
}

/**
 * Follows the user's recitation against a reference text. Words are identified by their index in
 * the reference passed to [load].
 */
interface Tracker {

    /** Sets the words to follow and puts the current position at [startIndex]; clears all state. */
    fun load(reference: List<ReferenceWord>, startIndex: Int = 0)

    /**
     * Adds [words] after the loaded ones without clearing anything: states, the position and the
     * phonemes heard so far stay, and the new words get the next indexes. Recitation that goes on
     * past the loaded words is followed into them.
     */
    fun append(words: List<ReferenceWord>)

    /** Feeds newly heard phonemes and returns the changes they caused, empty when none. */
    fun onPhonemes(events: List<PhonemeEvent>): List<TrackerEvent>

    /** Returns the state of the word at [index], [WordState.Neutral] when nothing was heard yet. */
    fun stateOfWord(index: Int): WordState

    /**
     * Returns the index of the word the user is expected to say next: after the last word said, so
     * it moves back while the user repeats words already said.
     */
    fun currentIndex(): Int

    /** Applies [config] to the phonemes fed from now on. */
    fun setConfig(config: TrackerConfig)

    /** Forgets everything heard so far and keeps the loaded reference. */
    fun reset()

    /**
     * Forgets the audio heard so far but keeps the states of the words before [index], and follows
     * on from the word at [index]: for a new microphone run that continues the same recitation,
     * whose phoneme times start again at zero.
     */
    fun continueFrom(index: Int)
}

interface TrackerFactory {
    fun create(config: TrackerConfig): Tracker
}

/** Backed by `ordered_quran_phonemes.json`. */
interface ReferencePhonemes {

    /** Returns the words of the ayah in order, or an empty list when the ayah does not exist. */
    fun forAyah(surah: Int, ayah: Int): List<ReferenceWord>
}
