package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.recite.ReviewFinding
import app.awn.quran.feature.recite.WordReview

/** Turns what the recitation flagged into the mistakes the Weak spots list is built from. */
object PracticeMistakes {

    /**
     * One record per ayah and kind for a recitation that stopped at [atMillis]. An ayah with a
     * skipped word gets Skipped; an ayah with a wrong word gets Other word; an ayah with both gets
     * both. Unclear words, letter substitutions and held-vowel timing are left out: only likely
     * mistakes of wording count.
     */
    fun recordsOf(reviews: Map<AyahRef, List<WordReview>>, atMillis: Long): List<MistakeRecord> =
        reviews.flatMap { (ayah, wordReviews) ->
            val findings = wordReviews.flatMap { it.findings }
            buildList {
                if (findings.any { it is ReviewFinding.Skipped }) {
                    add(MistakeRecord(ayah, MistakeType.Skipped, atMillis))
                }
                if (findings.any { it is ReviewFinding.WrongWord }) {
                    add(MistakeRecord(ayah, MistakeType.OtherWord, atMillis))
                }
            }
        }

    /** How many words of the ayahs inside [ayahs] were flagged; [ayahs] null counts every ayah. */
    fun mistakenWords(
        reviews: Map<AyahRef, List<WordReview>>,
        ayahs: ClosedRange<AyahRef>?,
    ): Int =
        reviews
            .filterKeys { ayahs == null || it in ayahs }
            .values
            .sumOf { wordReviews ->
                wordReviews.count { review ->
                    review.findings.any {
                        it is ReviewFinding.Skipped || it is ReviewFinding.WrongWord
                    }
                }
            }
}
