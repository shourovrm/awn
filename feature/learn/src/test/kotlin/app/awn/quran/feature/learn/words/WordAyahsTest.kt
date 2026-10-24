package app.awn.quran.feature.learn.words

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WordAyahsTest {

    @Test
    fun placesInTheSameAyahMakeOneRowInReadingOrder() {
        val places = listOf(WordKey(2, 5, 3), WordKey(2, 5, 9), WordKey(2, 7, 1), WordKey(3, 1, 2))

        val ayahs = wordAyahsOf(places)

        assertEquals(listOf(AyahRef(2, 5), AyahRef(2, 7), AyahRef(3, 1)), ayahs.map { it.ayah })
        assertEquals(listOf(WordKey(2, 5, 3), WordKey(2, 5, 9)), ayahs.first().wordKeys)
    }

    private val search =
        WordAyahSearch(
            listOf(
                row(2, 1, listOf("Al-Baqarah", "আল-বাকারা"), "This is the Book"),
                row(2, 10, listOf("Al-Baqarah", "আল-বাকারা"), "In their hearts is a disease"),
                row(2, 105, listOf("Al-Baqarah", "আল-বাকারা"), "Allah selects for His mercy"),
                row(12, 1, listOf("Yusuf", "ইউসুফ"), "These are the verses of the clear Book"),
                row(36, 1, listOf("Ya-Sin", "ইয়াসীন"), "Ya, Seen"),
            ),
        )

    private fun row(surah: Int, ayah: Int, names: List<String>, translation: String) =
        WordAyahSearchText(AyahRef(surah, ayah), names, listOf(translation))

    private fun refs(query: String): List<String> = search.matching(query).map { it.toString() }

    @Test
    fun anEmptyQueryKeepsEveryAyah() {
        assertEquals(listOf("2:1", "2:10", "2:105", "12:1", "36:1"), refs("  "))
    }

    @Test
    fun aNumberAloneIsTheSurah() {
        // "2" must not find 12:1 because its reference contains a 2.
        assertEquals(listOf("2:1", "2:10", "2:105"), refs("2"))
    }

    @Test
    fun aReferenceFindsAyahsThatStartWithIt() {
        assertEquals(listOf("2:1", "2:10", "2:105"), refs("2:1"))
        assertEquals(listOf("2:10", "2:105"), refs("2:10"))
    }

    @Test
    fun banglaDigitsWorkLikeLatinDigits() {
        assertEquals(listOf("2:10", "2:105"), refs("২:১০"))
    }

    @Test
    fun aSurahNameFindsItsAyahsWhateverTheSpelling() {
        assertEquals(listOf("2:1", "2:10", "2:105"), refs("baqarah"))
        assertEquals(listOf("36:1"), refs("ইয়াসিন"))
    }

    @Test
    fun wordsOfTheTranslationFindTheirAyah() {
        assertEquals(listOf("2:1", "12:1"), refs("book"))
    }
}
