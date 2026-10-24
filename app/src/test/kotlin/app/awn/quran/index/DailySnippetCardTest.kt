package app.awn.quran.index

import app.awn.quran.core.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class DailySnippetCardTest {

    private fun chapter(number: Int, nameSimple: String, nameBangla: String?): Chapter =
        Chapter(
            number = number,
            nameSimple = nameSimple,
            nameArabic = "",
            nameBangla = nameBangla,
            versesCount = 1,
            revelationPlace = "makkah",
            bismillahPre = true,
        )

    @Test
    fun usesTheBanglaNameWhenThePackHasOne() {
        val chapters = listOf(chapter(73, "Al-Muzzammil", "আল-মুযযাম্মিল"))

        assertEquals("আল-মুযযাম্মিল", banglaSurahName(chapters, 73))
    }

    @Test
    fun fallsBackToTheLatinNameWhenTheBanglaNameIsMissingOrBlank() {
        val chapters = listOf(chapter(73, "Al-Muzzammil", null), chapter(74, "Al-Muddaththir", " "))

        assertEquals("Al-Muzzammil", banglaSurahName(chapters, 73))
        assertEquals("Al-Muddaththir", banglaSurahName(chapters, 74))
    }
}
