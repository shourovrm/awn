package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTajweedProgressTest {

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
            val result = LegacyTajweedProgress.withCarriedOver(legacyLessons.toSet())

            assertTrue("$legacyLessons should finish $unit", unit in result)
        }
    }

    @Test
    fun `a unit that covers two older lessons needs both`() {
        val onlyOne = LegacyTajweedProgress.withCarriedOver(setOf("makharij"))
        val both = LegacyTajweedProgress.withCarriedOver(setOf("makharij", "sifat"))

        assertFalse("tajweed-11-where-letters-are-made" in onlyOne)
        assertTrue("tajweed-11-where-letters-are-made" in both)
    }

    @Test
    fun `other lessons and the older ids stay as they are`() {
        val done = setOf("letters-01", "grammar-03-pronoun-endings", "ghunnah")

        val result = LegacyTajweedProgress.withCarriedOver(done)

        assertEquals(done + "tajweed-15-nasal-sound", result)
    }

    @Test
    fun `no progress carries nothing over`() {
        assertEquals(emptySet<String>(), LegacyTajweedProgress.withCarriedOver(emptySet()))
    }
}
