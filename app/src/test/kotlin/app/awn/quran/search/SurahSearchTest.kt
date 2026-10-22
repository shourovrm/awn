package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user typed a surah name into the search on the Quran tab and got no surah back, because the
 * search only looked inside ayah text. [surahsMatching] finds surahs by number, by the
 * transliterated name in any common spelling, by the Arabic name and by the Bangla name.
 */
class SurahSearchTest {

    private fun chapter(number: Int, simple: String, arabic: String, bangla: String?) =
        Chapter(
            number = number,
            nameArabic = arabic,
            nameSimple = simple,
            nameBangla = bangla,
            versesCount = 7,
            revelationPlace = "makkah",
            bismillahPre = true,
        )

    private val chapters =
        listOf(
            chapter(1, "Al-Fatihah", "الفاتحة", "আল ফাতিহা"),
            chapter(2, "Al-Baqarah", "البقرة", "আল বাকারা"),
            chapter(3, "Ali 'Imran", "آل عمران", "আল ইমরান"),
            chapter(18, "Al-Kahf", "الكهف", "আল কাহফ"),
            chapter(36, "Ya-Sin", "يس", "ইয়াসীন"),
            chapter(55, "Ar-Rahman", "الرحمن", null),
            chapter(112, "Al-Ikhlas", "الإخلاص", "আল ইখলাস"),
        )

    private fun numbersFor(query: String) = surahsMatching(query, chapters).map { it.number }

    @Test
    fun theNameWithoutTheArticleFindsTheSurah() {
        assertEquals(listOf(2), numbersFor("baqarah"))
    }

    @Test
    fun aShortSpellingWithoutTheFinalHFindsTheSurah() {
        assertEquals(listOf(2), numbersFor("Baqara"))
    }

    @Test
    fun theArticleWithAHyphenASpaceOrNothingFindsTheSurah() {
        assertEquals(listOf(2), numbersFor("Al-Baqarah"))
        assertEquals(listOf(2), numbersFor("al baqarah"))
        assertEquals(listOf(2), numbersFor("albaqarah"))
    }

    @Test
    fun hyphensApostrophesAndSpacesInsideTheNameAreIgnored() {
        assertEquals(listOf(36), numbersFor("yasin"))
        assertEquals(listOf(36), numbersFor("ya sin"))
        assertEquals(listOf(3), numbersFor("imran"))
    }

    @Test
    fun theArabicNameFindsTheSurah() {
        assertEquals(listOf(18), numbersFor("الكهف"))
    }

    @Test
    fun theArabicNameWithoutTheArticleFindsTheSurah() {
        assertEquals(listOf(18), numbersFor("كهف"))
    }

    @Test
    fun theBanglaNameFindsTheSurah() {
        assertEquals(listOf(2), numbersFor("বাকারা"))
    }

    @Test
    fun aSurahWithoutABanglaNameIsStillFoundByItsOtherNames() {
        assertEquals(listOf(55), numbersFor("rahman"))
    }

    @Test
    fun theSurahNumberFindsThatSurahOnly() {
        assertEquals(listOf(18), numbersFor("18"))
        assertEquals(listOf(2), numbersFor(" 2 "))
    }

    @Test
    fun aNumberOutsideTheQuranFindsNothing() {
        assertTrue(numbersFor("115").isEmpty())
        assertTrue(numbersFor("0").isEmpty())
    }

    @Test
    fun namesThatStartWithTheQueryComeBeforeNamesThatOnlyContainIt() {
        // "ra" starts Ar-Rahman (after the article) and sits inside Al-Baqarah and Ali 'Imran.
        val found = numbersFor("ra")
        assertEquals(55, found.first())
        assertTrue(found.containsAll(listOf(2, 3)))
    }

    @Test
    fun aOneLetterQueryFindsNothing() {
        assertTrue(numbersFor("a").isEmpty())
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertTrue(numbersFor("   ").isEmpty())
    }

    @Test
    fun textThatIsInNoNameFindsNothing() {
        assertTrue(numbersFor("zzz").isEmpty())
    }
}
