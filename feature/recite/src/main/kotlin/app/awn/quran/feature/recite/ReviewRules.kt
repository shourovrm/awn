package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.player.AudioPlayer
import app.awn.quran.tracker.DurationCheck
import app.awn.quran.tracker.DurationVerdict
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState

/** One thing found about a word. */
sealed interface ReviewFinding {
    data object Skipped : ReviewFinding

    data object WrongWord : ReviewFinding

    data class Pronunciation(val substitution: TrackerEvent.Substitution) : ReviewFinding

    data class Duration(val check: DurationCheck) : ReviewFinding
}

/** Everything found about one word, in the order shown in the sheet. */
data class WordReview(val key: WordKey, val findings: List<ReviewFinding>)

internal object ReviewRules {

    /**
     * Lists the words to review, grouped by ayah number. A word is listed when it was skipped, is
     * wrong, has a substitution, or has a duration finding that is not [DurationVerdict.Ok]; a
     * skipped word is reported as skipped only, although the tracker also marks it wrong. Duration
     * findings that are Ok are kept on a listed word so the sheet can show them next to the bad
     * ones.
     */
    fun reviewsByAyah(
        keys: List<WordKey>,
        trackerStates: List<WordState>,
        skippedIndexes: Set<Int>,
        durations: Map<WordKey, List<DurationCheck>>,
        substitutions: Map<WordKey, List<TrackerEvent.Substitution>> = emptyMap(),
    ): Map<Int, List<WordReview>> {
        val reviews = mutableListOf<WordReview>()
        keys.forEachIndexed { index, key ->
            val findings = mutableListOf<ReviewFinding>()
            when {
                index in skippedIndexes -> findings += ReviewFinding.Skipped
                trackerStates[index] == WordState.Wrong -> findings += ReviewFinding.WrongWord
            }
            val wordSubstitutions = substitutions[key].orEmpty()
            val checks = durations[key].orEmpty()
            val hasBadDuration = checks.any { it.verdict != DurationVerdict.Ok }
            if (findings.isNotEmpty() || wordSubstitutions.isNotEmpty() || hasBadDuration) {
                findings += wordSubstitutions.map { ReviewFinding.Pronunciation(it) }
                findings += checks.map { ReviewFinding.Duration(it) }
                reviews += WordReview(key, findings)
            }
        }
        return reviews.groupBy { it.key.ayah }
    }
}

/** Plays the reciter's recording of one flagged word so the user can hear it said correctly. */
suspend fun playWord(audioPlayer: AudioPlayer, reciterId: String, key: WordKey) {
    audioPlayer.playWord(reciterId, AyahRef(key.surah, key.ayah), key.wordPosition)
}
