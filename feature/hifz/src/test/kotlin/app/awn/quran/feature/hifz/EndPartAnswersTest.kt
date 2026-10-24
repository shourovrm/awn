package app.awn.quran.feature.hifz

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.recite.ReviewFinding
import app.awn.quran.feature.recite.WordReview
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndPartAnswersTest {

    private val ayahs = (1..4).map(::ayah)

    private fun heard(vararg numbers: Int) = numbers.associate {
        WordKey(2, it, 1) to WordState.Confirmed
    }

    private fun wrongWords(ayahNumber: Int, count: Int) =
        ayah(ayahNumber) to
            (1..count).map {
                WordReview(WordKey(2, ayahNumber, it), listOf(ReviewFinding.WrongWord))
            }

    @Test
    fun withoutRecitationEveryAyahStartsFirmAndHasNoNote() {
        val answers = EndPartAnswers.forLesson(ayahs, emptyMap(), emptyMap())

        assertTrue(answers.answers.values.all { it == AyahFirmness.Firm })
        assertTrue(answers.notes.isEmpty())
        assertNull(answers.nextStart)
    }

    @Test
    fun recitedAyahsAreFirmOrShakyByTheirMistakesAndTheRestNotYet() {
        val answers =
            EndPartAnswers.forLesson(
                ayahs,
                heard(1, 2, 3),
                mapOf(wrongWords(2, 1), wrongWords(3, 2)),
            )

        assertEquals(
            listOf(AyahFirmness.Firm, AyahFirmness.Shaky, AyahFirmness.NotYet, AyahFirmness.NotYet),
            ayahs.map { answers.answers.getValue(it) },
        )
        assertEquals(RecitationNote.NotRecited, answers.notes[ayah(4)])
        assertEquals(RecitationNote.WordsWrong(2), answers.notes[ayah(3)])
        assertEquals(2, answers.memorisedCount)
        assertEquals(ayah(3), answers.nextStart)
    }

    @Test
    fun changingAnAnswerMovesTheNextStart() {
        val answers =
            EndPartAnswers.forLesson(ayahs, heard(1, 2, 3), mapOf(wrongWords(3, 2)))
                .with(ayah(3), AyahFirmness.Shaky)

        assertEquals(ayah(4), answers.nextStart)
    }

    @Test
    fun aPageNotReachedHasNoGradeAndIsLeftOutOfTheSave() {
        val table = smallTable()
        val answers =
            EndPartAnswers.forRevision(listOf(1, 2), table::ayahsOn, heard(1, 2), emptyMap())

        assertEquals(PartGrade.Good, answers.answers[1])
        assertNull(answers.answers[2])
        assertEquals(mapOf(1 to PartGrade.Good), answers.grades)
        assertEquals(1, answers.revisedCount)
    }
}
