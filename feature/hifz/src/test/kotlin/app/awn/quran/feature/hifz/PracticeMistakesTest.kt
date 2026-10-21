package app.awn.quran.feature.hifz

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.recite.ReviewFinding
import app.awn.quran.feature.recite.WordReview
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeMistakesTest {

    private fun review(ayahNumber: Int, position: Int, vararg findings: ReviewFinding) =
        WordReview(WordKey(2, ayahNumber, position), findings.toList())

    @Test
    fun skippedAndWrongWordsBecomeOneRecordEachPerAyah() {
        val reviews =
            mapOf(
                ayah(155) to
                    listOf(
                        review(155, 1, ReviewFinding.Skipped),
                        review(155, 4, ReviewFinding.Skipped),
                    ),
                ayah(149) to listOf(review(149, 2, ReviewFinding.WrongWord)),
                ayah(150) to
                    listOf(review(150, 1, ReviewFinding.Skipped, ReviewFinding.WrongWord)),
            )

        val records = PracticeMistakes.recordsOf(reviews, atMillis = 9)

        assertEquals(
            setOf(
                MistakeRecord(ayah(155), MistakeType.Skipped, 9),
                MistakeRecord(ayah(149), MistakeType.OtherWord, 9),
                MistakeRecord(ayah(150), MistakeType.Skipped, 9),
                MistakeRecord(ayah(150), MistakeType.OtherWord, 9),
            ),
            records.toSet(),
        )
        assertEquals(4, records.size)
    }

    @Test
    fun unclearWordsAreNotMistakes() {
        val reviews = mapOf(ayah(1) to listOf(review(1, 1, ReviewFinding.NotClear)))

        assertEquals(emptyList<MistakeRecord>(), PracticeMistakes.recordsOf(reviews, 1))
    }

    @Test
    fun wordsAreCountedOnlyInsideTheLessonsAyahs() {
        val reviews =
            mapOf(
                ayah(10) to listOf(review(10, 1, ReviewFinding.Skipped)),
                ayah(11) to
                    listOf(
                        review(11, 1, ReviewFinding.WrongWord),
                        review(11, 2, ReviewFinding.Skipped),
                    ),
                ayah(20) to listOf(review(20, 1, ReviewFinding.Skipped)),
            )

        assertEquals(3, PracticeMistakes.mistakenWords(reviews, ayah(10)..ayah(12)))
        assertEquals(4, PracticeMistakes.mistakenWords(reviews, null))
    }
}
