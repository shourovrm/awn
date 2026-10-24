package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.PageTable
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.recite.WordReview
import app.awn.quran.tracker.WordState

/** What the recitation says of one row of the End part sheet. */
sealed interface RecitationNote {
    data object NotRecited : RecitationNote

    /** [count] words were flagged; zero means no mistakes. */
    data class WordsWrong(val count: Int) : RecitationNote
}

/**
 * The answers of the End part sheet while the user edits them. A lesson has one answer per ayah, a
 * revision one per page, where null is a page not reached. [notes] are empty when the user did not
 * recite at all, because then there is nothing to report.
 */
sealed interface EndPartAnswers {
    data class Lesson(
        val ayahs: List<AyahRef>,
        val answers: Map<AyahRef, AyahFirmness>,
        val notes: Map<AyahRef, RecitationNote>,
    ) : EndPartAnswers {
        val memorisedCount: Int
            get() = answers.values.count { it.isMemorised }

        /** Where the next lesson starts: the first ayah not yet memorised, null when none is. */
        val nextStart: AyahRef?
            get() = ayahs.firstOrNull { !(answers[it] ?: AyahFirmness.NotYet).isMemorised }

        fun with(ayah: AyahRef, firmness: AyahFirmness) =
            copy(answers = answers + (ayah to firmness))
    }

    data class Revision(
        val pages: List<Int>,
        val answers: Map<Int, PartGrade?>,
        val notes: Map<Int, RecitationNote>,
    ) : EndPartAnswers {
        val revisedCount: Int
            get() = answers.values.count { it != null }

        /** The pages reached and their grades, as the controller saves them. */
        val grades: Map<Int, PartGrade>
            get() = answers.mapNotNull { (page, grade) -> grade?.let { page to it } }.toMap()

        fun with(page: Int, grade: PartGrade?) = copy(answers = answers + (page to grade))
    }

    companion object {
        fun forLesson(
            ayahs: List<AyahRef>,
            wordStates: Map<WordKey, WordState>,
            reviews: Map<AyahRef, List<WordReview>>,
        ) =
            Lesson(
                ayahs = ayahs,
                answers = EndPartSuggestions.lessonFirmness(ayahs, wordStates, reviews),
                notes = EndPartSuggestions.notesFor(ayahs, wordStates, reviews),
            )

        fun forRevision(
            pages: List<Int>,
            ayahsOnPage: (page: Int) -> List<AyahRef>,
            wordStates: Map<WordKey, WordState>,
            reviews: Map<AyahRef, List<WordReview>>,
        ): Revision {
            val notesByAyah =
                EndPartSuggestions.notesFor(pages.flatMap(ayahsOnPage), wordStates, reviews)
            return Revision(
                pages = pages,
                answers =
                    EndPartSuggestions.revisionGrades(pages, ayahsOnPage, wordStates, reviews),
                notes =
                    pages
                        .mapNotNull { page ->
                            pageNote(ayahsOnPage(page), notesByAyah)?.let { note -> page to note }
                        }
                        .toMap(),
            )
        }

        // A page is recited when any of its ayahs is; its flagged words add up.
        private fun pageNote(
            ayahs: List<AyahRef>,
            notes: Map<AyahRef, RecitationNote>,
        ): RecitationNote? {
            val pageNotes = ayahs.mapNotNull { notes[it] }
            val wrong = pageNotes.filterIsInstance<RecitationNote.WordsWrong>()
            return when {
                pageNotes.isEmpty() -> null
                wrong.isEmpty() -> RecitationNote.NotRecited
                else -> RecitationNote.WordsWrong(wrong.sumOf { it.count })
            }
        }
    }
}

/** The answers End part starts with for [request], read from the recitation so far. */
internal fun startingAnswers(
    request: PracticeRequest,
    table: PageTable,
    wordStates: Map<WordKey, WordState>,
    reviews: Map<AyahRef, List<WordReview>>,
): EndPartAnswers =
    if (request.kind == PartKind.NewLesson) {
        val ayahs = table.allAyahs.filter { it in request.firstAyah..request.lastAyah }
        EndPartAnswers.forLesson(ayahs, wordStates, reviews)
    } else {
        EndPartAnswers.forRevision(request.pages, table::ayahsOn, wordStates, reviews)
    }

private const val PREVIEW_WORDS = 6

/** The first words of each ayah the page has loaded, for the rows of the End part sheet. */
internal fun firstWordsByAyah(words: List<Word>): Map<AyahRef, String> =
    words
        .groupBy { AyahRef(it.key.surah, it.key.ayah) }
        .mapValues { (_, ayahWords) ->
            ayahWords.take(PREVIEW_WORDS).joinToString(" ") { it.textUthmani }
        }
