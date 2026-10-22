package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyLessonProgressTest {

    private val unitOfLegacyLesson =
        mapOf(
            "makharij" to "tajweed-11-where-letters-are-made",
            "sifat" to "tajweed-11-where-letters-are-made",
            "raa" to "tajweed-12-heavy-and-light-letters",
            "lam-of-allah" to "tajweed-12-heavy-and-light-letters",
            "noon-sakinah-tanween" to "tajweed-13-noon-saakin-and-tanween",
            "meem-sakinah" to "tajweed-14-meem-saakin",
            "ghunnah" to "tajweed-15-nasal-sound",
            "qalqalah" to "tajweed-16-echo-letters",
            "madd-natural" to "tajweed-17-long-vowels-held-longer",
            "madd-extended" to "tajweed-17-long-vowels-held-longer",
            "hamzat-al-wasl" to "tajweed-18-stopping-and-starting",
            "waqf-signs" to "tajweed-19-stop-signs",
        )

    @Test
    fun `finishing every older lesson of a unit finishes the unit`() {
        val legacyLessonsByUnit = unitOfLegacyLesson.entries.groupBy({ it.value }, { it.key })

        legacyLessonsByUnit.forEach { (unit, legacyLessons) ->
            val result = LegacyLessonProgress.withCarriedOver(legacyLessons.toSet())

            assertTrue("$legacyLessons should finish $unit", unit in result)
        }
    }

    @Test
    fun `finishing an older reading unit finishes the unit that replaced it`() {
        val expected =
            mapOf(
                "letters-03-short-vowels" to "letters-04-short-vowels",
                "letters-04-tanween" to "letters-05-tanween",
                "letters-05-long-vowels" to "letters-07-madd-and-leen",
                "letters-06-sukoon" to "letters-08-sukoon",
                "letters-07-shaddah" to "letters-09-shaddah",
                "letters-08-first-ayah" to "letters-10-first-ayah",
                "letters-09-first-ayah" to "letters-10-first-ayah",
                "letters-08-letters-by-name" to "letters-03-letters-by-name",
            )

        expected.forEach { (older, replacement) ->
            val result = LegacyLessonProgress.withCarriedOver(setOf(older))

            assertTrue("$older should finish $replacement", replacement in result)
        }
    }

    @Test
    fun `a unit that covers two older lessons needs both`() {
        val onlyOne = LegacyLessonProgress.withCarriedOver(setOf("makharij"))
        val both = LegacyLessonProgress.withCarriedOver(setOf("makharij", "sifat"))

        assertFalse("tajweed-11-where-letters-are-made" in onlyOne)
        assertTrue("tajweed-11-where-letters-are-made" in both)
    }

    @Test
    fun `other lessons and the older ids stay as they are`() {
        val done = setOf("letters-01", "grammar-03-pronoun-endings", "ghunnah")

        val result = LegacyLessonProgress.withCarriedOver(done)

        assertEquals(done + "tajweed-15-nasal-sound", result)
    }

    @Test
    fun `no progress carries nothing over`() {
        assertEquals(emptySet<String>(), LegacyLessonProgress.withCarriedOver(emptySet()))
    }
}
