package app.awn.quran.feature.learn.content

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json is only a stub on the plain JVM, so the parsing test needs Robolectric.
@RunWith(RobolectricTestRunner::class)
class SnippetSourceTest {

    private val surahNames = mapOf(73 to "আল-মুযযাম্মিল", 2 to "আল-বাকারা")

    private fun reference(source: SnippetSource, language: LearnLanguage): String =
        snippetReference(source, language) { surah -> surahNames.getValue(surah) }

    @Test
    fun anAyahInEnglishReadsQuranSurahColonAyah() {
        assertEquals(
            "Quran 73:4",
            reference(SnippetSource.Ayah(surah = 73, ayah = 4), LearnLanguage.English),
        )
    }

    @Test
    fun anAyahInBanglaNamesTheSurahAndUsesBanglaDigits() {
        assertEquals(
            "সূরা আল-মুযযাম্মিল, ৭৩:৪",
            reference(SnippetSource.Ayah(surah = 73, ayah = 4), LearnLanguage.Bangla),
        )
    }

    @Test
    fun aMultiDigitAyahKeepsEveryDigit() {
        assertEquals(
            "সূরা আল-বাকারা, ২:১৫২",
            reference(SnippetSource.Ayah(surah = 2, ayah = 152), LearnLanguage.Bangla),
        )
    }

    @Test
    fun aHadithInEnglishKeepsTheCollectionNameAndNumber() {
        assertEquals(
            "Sahih al-Bukhari 5027",
            reference(
                SnippetSource.Hadith(HadithCollection.Bukhari, "5027"),
                LearnLanguage.English,
            ),
        )
        assertEquals(
            "Sahih Muslim 804a",
            reference(SnippetSource.Hadith(HadithCollection.Muslim, "804a"), LearnLanguage.English),
        )
    }

    @Test
    fun aHadithInBanglaUsesTheBanglaNameAndDigitsOnly() {
        assertEquals(
            "সহীহ বুখারী, হাদিস ৫০২৭",
            reference(SnippetSource.Hadith(HadithCollection.Bukhari, "5027"), LearnLanguage.Bangla),
        )
        assertEquals(
            "সহীহ মুসলিম, হাদিস ৮০৪",
            reference(SnippetSource.Hadith(HadithCollection.Muslim, "804a"), LearnLanguage.Bangla),
        )
    }

    @Test
    fun englishDoesNotAskForASurahName() {
        val reference =
            snippetReference(SnippetSource.Ayah(73, 4), LearnLanguage.English) {
                error("The English reference needs no surah name.")
            }

        assertEquals("Quran 73:4", reference)
    }

    @Test
    fun theBundledSnippetsParseIntoSources() {
        val json =
            """{"snippets":[
              {"id":"quran-73-4","arabic":"a","source":{"type":"quran","surah":73,"ayah":4},
               "text_en":"e","text_bn":"b","url":null,"grade":null},
              {"id":"muslim-804a","arabic":"a",
               "source":{"type":"hadith","collection":"muslim","number":"804a"},
               "text_en":"e","text_bn":"b","url":"https://sunnah.com/muslim:804a","grade":"sahih"}
            ]}"""

        val snippets = SnippetRepository.parse(json)

        assertEquals(SnippetSource.Ayah(73, 4), snippets[0].source)
        assertEquals(SnippetSource.Hadith(HadithCollection.Muslim, "804a"), snippets[1].source)
    }
}
