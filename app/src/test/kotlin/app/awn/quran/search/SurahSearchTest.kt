package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user typed a surah name into the search on the Quran tab and got no surah back, because the
 * search only looked inside ayah text. [surahsMatching] finds surahs by number, by the
 * transliterated name in any common spelling, by the Arabic name, and by the spoken Bangla name and
 * its meaning in the spellings Bangla readers type.
 */
class SurahSearchTest {

    private fun chapter(
        number: Int,
        simple: String,
        arabic: String,
        bangla: String?,
        banglaMeaning: String? = null,
    ) =
        Chapter(
            number = number,
            nameArabic = arabic,
            nameSimple = simple,
            nameBangla = bangla,
            versesCount = 7,
            revelationPlace = "makkah",
            bismillahPre = true,
            nameBanglaMeaning = banglaMeaning,
        )

    private val chapters =
        listOf(
            chapter(1, "Al-Fatihah", "الفاتحة", "আল-ফাতিহা", "সূচনা"),
            chapter(2, "Al-Baqarah", "البقرة", "আল-বাকারা", "বকনা-বাছুর-গরু"),
            chapter(3, "Ali 'Imran", "آل عمران", "আল-ইমরান", "ইমরানের পরিবার"),
            chapter(18, "Al-Kahf", "الكهف", "আল-কাহফ", "গুহা"),
            chapter(36, "Ya-Sin", "يس", "ইয়াসীন", null),
            chapter(42, "Ash-Shuraa", "الشورى", "আশ-শূরা", "পরামর্শ"),
            chapter(50, "Qaf", "ق", "ক্বাফ", "আরবি বর্ণ ক্বাফ"),
            chapter(55, "Ar-Rahman", "الرحمن", null),
            chapter(67, "Al-Mulk", "الملك", "আল-মুলক", "সার্বভৌম কর্তৃত্ব"),
            chapter(112, "Al-Ikhlas", "الإخلاص", "আল-ইখলাস", "একনিষ্ঠতা"),
            chapter(113, "Al-Falaq", "الفلق", "আল-ফালাক", "নিশিভোর"),
            chapter(114, "An-Nas", "الناس", "আন-নাস", "মানবজাতি"),
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
    fun theSpokenBanglaNameFindsTheSurah() {
        assertEquals(listOf(2), numbersFor("বাকারা"))
        assertEquals(listOf(2), numbersFor("আল-বাকারা"))
        assertEquals(listOf(2), numbersFor("আল বাকারা"))
        assertEquals(listOf(2), numbersFor("আলবাকারা"))
    }

    @Test
    fun aSurahWithoutABanglaNameIsStillFoundByItsOtherNames() {
        assertEquals(listOf(55), numbersFor("rahman"))
    }

    @Test
    fun aLongVowelTypedShortFindsTheSurah() {
        assertEquals(listOf(36), numbersFor("ইয়াসিন"))
        assertEquals(listOf(1), numbersFor("ফাতিহা"))
        assertEquals(listOf(1), numbersFor("ফাতীহা"))
    }

    @Test
    fun aVowelSignTypedWithTheWrongQualityFindsTheSurah() {
        assertEquals(listOf(1), numbersFor("ফাতেহা"))
    }

    @Test
    fun aLeadingSuraWordIsIgnored() {
        assertEquals(listOf(67), numbersFor("সুরা মুলক"))
        assertEquals(listOf(67), numbersFor("সূরা মুলক"))
        assertEquals(listOf(67), numbersFor("সূরামুলক"))
        assertEquals(listOf(67), numbersFor("সূরা আল-মুলক"))
    }

    @Test
    fun aSoundAlikeConsonantFindsTheSurah() {
        assertEquals(listOf(112), numbersFor("ইখলাছ"))
        assertEquals(listOf(112), numbersFor("ইখলাশ"))
    }

    @Test
    fun aConjunctWithBaFindsTheNameTypedWithoutIt() {
        assertEquals(listOf(50), numbersFor("কাফ"))
        assertEquals(listOf(50), numbersFor("ক্বাফ"))
    }

    @Test
    fun theArticleVariantAlEFindsTheSurah() {
        assertEquals(listOf(3), numbersFor("আলে ইমরান"))
    }

    @Test
    fun aSunLetterArticleIsOptional() {
        assertEquals(listOf(114), numbersFor("নাস"))
        assertEquals(listOf(114), numbersFor("আন নাস"))
        assertEquals(listOf(114), numbersFor("আননাস"))
    }

    @Test
    fun theMeaningFindsTheSurah() {
        assertEquals(listOf(18), numbersFor("গুহা"))
        assertEquals(listOf(113), numbersFor("নিশিভোর"))
        assertEquals(listOf(2), numbersFor("বকনা"))
    }

    @Test
    fun theSurahNumberFindsThatSurahOnly() {
        assertEquals(listOf(18), numbersFor("18"))
        assertEquals(listOf(2), numbersFor(" 2 "))
    }

    @Test
    fun banglaDigitsFindTheSurahNumber() {
        assertEquals(listOf(114), numbersFor("১১৪"))
        assertEquals(listOf(36), numbersFor("৩৬"))
        assertEquals(listOf(36), numbersFor("সূরা ৩৬"))
    }

    @Test
    fun aNumberOutsideTheQuranFindsNothing() {
        assertTrue(numbersFor("115").isEmpty())
        assertTrue(numbersFor("0").isEmpty())
        assertTrue(numbersFor("১১৫").isEmpty())
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
        assertTrue(numbersFor("আ").isEmpty())
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertTrue(numbersFor("   ").isEmpty())
    }

    @Test
    fun theNameShuraIsNotEatenByTheSuraWord() {
        // শূরা and সূরা fold to the same letters.
        assertEquals(listOf(42), numbersFor("শূরা"))
        assertEquals(listOf(42), numbersFor("সূরা শূরা"))
        assertEquals(listOf(42), numbersFor("সূরা আশ-শূরা"))
    }

    @Test
    fun textThatIsInNoNameFindsNothing() {
        assertTrue(numbersFor("zzz").isEmpty())
    }
}
