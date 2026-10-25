package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.HifzProgress
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.recite.WordReview
import app.awn.quran.tracker.WordState

/**
 * The answers the End part sheet starts with, read from the recitation of the part. The user can
 * change every one; these only save taps.
 */
object EndPartSuggestions {

    /**
     * One answer per ayah of a lesson. Without any recitation the user is the only judge, so every
     * ayah starts at Firm. Otherwise an ayah recited with no flagged word is Firm, with one it is
     * Shaky, and with more, or not recited, it is Not yet.
     */
    fun lessonFirmness(
        ayahs: List<AyahRef>,
        wordStates: Map<WordKey, WordState>,
        reviews: Map<AyahRef, List<WordReview>>,
    ): Map<AyahRef, AyahFirmness> {
        val recited = recitedAyahs(wordStates)
        if (recited.isEmpty()) return ayahs.associateWith { AyahFirmness.Firm }
        return ayahs.associateWith { ayah ->
            val mistakes = PracticeMistakes.mistakenWords(reviews, ayah..ayah)
            when {
                ayah !in recited -> AyahFirmness.NotYet
                mistakes == 0 -> AyahFirmness.Firm
                mistakes == 1 -> AyahFirmness.Shaky
                else -> AyahFirmness.NotYet
            }
        }
    }

    /**
     * One answer per page of a revision; null marks a page the user did not reach. Without any
     * recitation every page starts at Good; otherwise a recited page gets the grade its flagged
     * words suggest.
     */
    fun revisionGrades(
        pages: List<Int>,
        ayahsOnPage: (page: Int) -> List<AyahRef>,
        wordStates: Map<WordKey, WordState>,
        reviews: Map<AyahRef, List<WordReview>>,
    ): Map<Int, PartGrade?> {
        val recited = recitedAyahs(wordStates)
        if (recited.isEmpty()) return pages.associateWith { PartGrade.Good }
        return pages.associateWith { page ->
            val ayahs = ayahsOnPage(page)
            if (ayahs.none { it in recited }) {
                null
            } else {
                val mistakes = PracticeMistakes.mistakenWords(reviews, ayahs.first()..ayahs.last())
                HifzProgress.suggestedGrade(mistakes, ayahs.size)
            }
        }
    }

    /** What the recitation says of each of [ayahs]; empty when the user did not recite at all. */
    fun notesFor(
        ayahs: List<AyahRef>,
        wordStates: Map<WordKey, WordState>,
        reviews: Map<AyahRef, List<WordReview>>,
    ): Map<AyahRef, RecitationNote> {
        val recited = recitedAyahs(wordStates)
        if (recited.isEmpty()) return emptyMap()
        return ayahs.associateWith { ayah ->
            if (ayah in recited) {
                RecitationNote.WordsWrong(PracticeMistakes.mistakenWords(reviews, ayah..ayah))
            } else {
                RecitationNote.NotRecited
            }
        }
    }

    /**
     * The ayahs with at least one word the tracker heard, right or wrong; a passed-over word is
     * not.
     */
    private fun recitedAyahs(wordStates: Map<WordKey, WordState>): Set<AyahRef> =
        wordStates
            .filterValues {
                it != WordState.Neutral && it != WordState.Hidden && it != WordState.Unread
            }
            .keys
            .map { AyahRef(it.surah, it.ayah) }
            .toSet()
}
