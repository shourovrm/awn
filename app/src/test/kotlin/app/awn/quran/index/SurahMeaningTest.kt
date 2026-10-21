package app.awn.quran.index

import app.awn.quran.core.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurahMeaningTest {

    private val english = listOf("The Opener", "The Cow")

    private fun chapter(number: Int, bangla: String?) =
        Chapter(number, "", "Surah $number", bangla, 7, "makkah", true)

    @Test
    fun banglaUsersGetTheBanglaMeaning() {
        assertEquals("সূচনা", surahMeaningFor("bn", chapter(1, "সূচনা"), english))
    }

    @Test
    fun banglaFallsBackToEnglishWhenThePackHasNone() {
        assertEquals("The Cow", surahMeaningFor("bn", chapter(2, null), english))
        assertEquals("The Cow", surahMeaningFor("bn", chapter(2, " "), english))
    }

    @Test
    fun everyOtherLanguageGetsEnglish() {
        assertEquals("The Opener", surahMeaningFor("en", chapter(1, "সূচনা"), english))
        assertEquals("The Opener", surahMeaningFor("ur", chapter(1, "সূচনা"), english))
    }

    @Test
    fun aSurahWithNoMeaningAnywhereHasNone() {
        assertNull(surahMeaningFor("en", chapter(3, null), english))
    }
}
