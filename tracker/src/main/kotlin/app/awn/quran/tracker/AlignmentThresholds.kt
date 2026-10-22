package app.awn.quran.tracker

import kotlin.math.roundToInt

/**
 * The numbers the tracker judges with, all derived from one [TrackerConfig.speed] value.
 *
 * Every threshold is a straight line between a patient value (speed 0) and an eager value (speed
 * 1). The default speed 0.5 gives the values in docs/plan.md: insertion at 6 unmatched phonemes,
 * re-anchoring at 12. Whether a word is green is decided by [WordScore], not by the speed.
 * "Phoneme" here is one character of a model token, after runs of one repeated character are
 * merged into one.
 *
 * @property wrongSubstitutions substitutions that make a word red
 * @property skipEvidencePhonemes matched phonemes needed after a left-out word before it is flagged
 * @property insertionPhonemes consecutive unmatched heard phonemes that count as an insertion
 * @property reanchorPhonemes consecutive unmatched heard phonemes that trigger a search of the
 *   surah
 * @property entryMatches matched phonemes needed before the first word is judged, so that stray
 *   sounds do not colour words
 */
internal data class AlignmentThresholds(
    val wrongSubstitutions: Int,
    val skipEvidencePhonemes: Int,
    val insertionPhonemes: Int,
    val reanchorPhonemes: Int,
    val entryMatches: Int,
) {
    companion object {
        private const val SKIP_EVIDENCE_PATIENT = 8f
        private const val SKIP_EVIDENCE_EAGER = 3f
        private const val INSERTION_PATIENT = 8f
        private const val INSERTION_EAGER = 4f
        private const val REANCHOR_PATIENT = 16f
        private const val REANCHOR_EAGER = 8f
        private const val ENTRY_PATIENT = 7f
        private const val ENTRY_EAGER = 5f
        private const val WRONG_SUBSTITUTIONS = 2

        fun forSpeed(speed: Float): AlignmentThresholds {
            val clampedSpeed = speed.coerceIn(0f, 1f)
            return AlignmentThresholds(
                wrongSubstitutions = WRONG_SUBSTITUTIONS,
                skipEvidencePhonemes =
                    between(SKIP_EVIDENCE_PATIENT, SKIP_EVIDENCE_EAGER, clampedSpeed).roundToInt(),
                insertionPhonemes =
                    between(INSERTION_PATIENT, INSERTION_EAGER, clampedSpeed).roundToInt(),
                reanchorPhonemes =
                    between(REANCHOR_PATIENT, REANCHOR_EAGER, clampedSpeed).roundToInt(),
                entryMatches = between(ENTRY_PATIENT, ENTRY_EAGER, clampedSpeed).roundToInt(),
            )
        }

        private fun between(patientValue: Float, eagerValue: Float, speed: Float): Float =
            patientValue + (eagerValue - patientValue) * speed
    }
}
