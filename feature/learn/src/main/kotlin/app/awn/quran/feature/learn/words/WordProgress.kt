package app.awn.quran.feature.learn.words

import app.awn.quran.feature.learn.data.GrammarCardEntity
import app.awn.quran.feature.learn.data.WordCardEntity
import app.awn.quran.feature.learn.fsrs.FsrsCard
import app.awn.quran.feature.learn.fsrs.FsrsState

/** What the word list says about a word. */
enum class WordStatus {
    /** The word has not been shown yet. */
    NotMet,

    /** Shown, but not yet recalled across a gap of several days. */
    Learning,

    /** Recalled correctly after at least two reviews spaced days apart. */
    Known,
}

/**
 * The figures behind "words met", "words known" and "share of the Quran". Only the user's own
 * answers in sessions move a card, so a self-started quiz never changes these figures.
 */
object WordProgress {

    /**
     * A word is known once its stability, the days FSRS expects it to stay in memory, is at least
     * this and it is not being relearned. A first correct answer gives about two days and a second
     * one, after that gap, about eight, so "known" means two correct answers on separate days.
     */
    const val KNOWN_STABILITY_DAYS = 4.0

    /** A word forgotten this many times is weak: it is asked in the easiest types only. */
    const val WEAK_LAPSES = 2

    fun statusOf(card: WordCardEntity?): WordStatus =
        when {
            card == null -> WordStatus.NotMet
            isKnown(card) -> WordStatus.Known
            else -> WordStatus.Learning
        }

    fun isKnown(card: WordCardEntity): Boolean =
        card.state == FsrsState.Review.value &&
            (card.stability ?: 0.0) >= KNOWN_STABILITY_DAYS

    fun isWeak(card: WordCardEntity): Boolean = card.lapses >= WEAK_LAPSES

    /** The share of all Quran words that the forms in [knownRanks] cover, from 0.0 to 1.0. */
    fun coverage(knownRanks: Collection<Int>, frequency: WordFrequency): Double {
        val covered = knownRanks.sumOf { frequency.occurrenceCount(it) }
        return covered.toDouble() / frequency.wordCount
    }

    /** The known forms' coverage after learning the [count] most frequent forms, for the chart. */
    fun coverageOfTopForms(count: Int, frequency: WordFrequency): Double =
        coverage((1..count.coerceAtMost(frequency.formCount)).toList(), frequency)
}

/** The FSRS card of a word, for the scheduler. */
fun WordCardEntity.toFsrsCard(): FsrsCard =
    FsrsCard(
        state = FsrsState.entries.first { it.value == state },
        step = step,
        stability = stability,
        difficulty = difficulty,
        dueMillis = due,
        lastReviewMillis = lastReview,
    )

/** The FSRS card of a grammar point, for the scheduler. */
fun GrammarCardEntity.toFsrsCard(): FsrsCard =
    FsrsCard(
        state = FsrsState.entries.first { it.value == state },
        step = step,
        stability = stability,
        difficulty = difficulty,
        dueMillis = due,
        lastReviewMillis = lastReview,
    )
