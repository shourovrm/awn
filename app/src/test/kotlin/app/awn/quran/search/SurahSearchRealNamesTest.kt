package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Searches the 114 real names in `tools/data/surah_names_bn.tsv`, the file that becomes the
 * `chapter` table, so a folding rule that breaks one name is caught here and not on a phone.
 */
class SurahSearchRealNamesTest {

    private val namesFile = File("../tools/data/surah_names_bn.tsv")

    private val chapters: List<Chapter> =
        namesFile
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("number") }
            .map { line ->
                val columns = line.split("\t")
                Chapter(
                    number = columns[0].toInt(),
                    nameArabic = "",
                    nameSimple = "Surah ${columns[0]}",
                    nameBangla = columns[1],
                    versesCount = 7,
                    revelationPlace = "makkah",
                    bismillahPre = true,
                    nameBanglaMeaning = columns[2].ifBlank { null },
                )
            }

    @Test
    fun theFileHolds114Surahs() {
        assertTrue(chapters.size == 114)
    }

    @Test
    fun everySpokenNameFindsItsOwnSurah() {
        for (chapter in chapters) {
            val found = surahsMatching(chapter.nameBangla.orEmpty(), chapters).map { it.number }
            assertTrue("${chapter.nameBangla} found $found", chapter.number in found)
        }
    }

    @Test
    fun everySpokenNameWithoutItsArticleFindsItsOwnSurah() {
        for (chapter in chapters) {
            val withoutArticle = chapter.nameBangla.orEmpty().replace(Regex("^[^\\s-]+[\\s-]"), "")
            val found = surahsMatching(withoutArticle, chapters).map { it.number }
            assertTrue("$withoutArticle found $found", chapter.number in found)
        }
    }

    @Test
    fun everySpokenNameWithTheSuraWordInFrontFindsItsOwnSurah() {
        for (chapter in chapters) {
            val found = surahsMatching("সূরা ${chapter.nameBangla}", chapters).map { it.number }
            assertTrue("সূরা ${chapter.nameBangla} found $found", chapter.number in found)
        }
    }

    @Test
    fun everyMeaningFindsItsOwnSurah() {
        for (chapter in chapters) {
            val meaning = chapter.nameBanglaMeaning ?: continue
            val found = surahsMatching(meaning, chapters).map { it.number }
            assertTrue("$meaning found $found", chapter.number in found)
        }
    }

    @Test
    fun everySurahNumberFindsItsOwnSurahInBanglaDigits() {
        val banglaDigits = "০১২৩৪৫৬৭৮৯"
        for (chapter in chapters) {
            val typed = chapter.number.toString().map { banglaDigits[it - '0'] }.joinToString("")
            val found = surahsMatching(typed, chapters).map { it.number }
            assertTrue("$typed found $found", found == listOf(chapter.number))
        }
    }
}
