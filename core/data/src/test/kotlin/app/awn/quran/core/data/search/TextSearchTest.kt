package app.awn.quran.core.data.search

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    private val saheeh = SearchSource.Translation(TranslationId("en_saheeh"))

    private val arabic =
        SearchableTexts(
            SearchSource.Arabic,
            listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(2, 153), AyahRef(2, 255)),
            listOf(
                "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
                "يَٰٓأَيُّهَا ٱلَّذِينَ ءَامَنُوا۟ ٱسْتَعِينُوا۟ بِٱلصَّبْرِ وَٱلصَّلَوٰةِ",
                "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ",
            ),
        )

    private val english =
        SearchableTexts(
            saheeh,
            listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(2, 153), AyahRef(2, 255)),
            listOf(
                "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
                "[All] praise is [due] to Allah, Lord of the worlds -",
                "O you who have believed, seek help through patience and prayer.",
                "Allah - there is no deity except Him, the Ever-Living, the Sustainer of existence.",
            ),
        )

    private var loadCount = 0

    private val search =
        TextSearch(
            loadTexts = {
                loadCount += 1
                listOf(arabic, english)
            },
        )

    @Test
    fun anArabicQueryWithoutVowelSignsFindsTheVocalisedText() = runBlocking {
        val results = search.search("الرحمن")

        assertEquals(listOf(AyahRef(1, 1)), results.hits.map { it.ayah })
        assertEquals(SearchSource.Arabic, results.hits.single().source)
    }

    @Test
    fun anyAlefFormInTheQueryFindsTheAyah() = runBlocking {
        val plain = search.search("لا اله")
        val withHamza = search.search("لآ إِلَٰه")

        assertEquals(listOf(AyahRef(2, 255)), plain.hits.map { it.ayah })
        assertEquals(plain.hits.map { it.ayah }, withHamza.hits.map { it.ayah })
    }

    @Test
    fun aQueryTypedWithVowelSignsStillMatches() = runBlocking {
        val results = search.search("ٱلصَّبْرِ")

        assertEquals(listOf(AyahRef(2, 153)), results.hits.map { it.ayah })
    }

    @Test
    fun theMatchedRangesCoverTheMatchedLettersWithTheirMarks() = runBlocking {
        val hit = search.search("الصبر").hits.single()

        assertEquals(1, hit.matches.size)
        assertEquals("ٱلصَّبْرِ", hit.text.substring(hit.matches.single()))
    }

    @Test
    fun aRepeatedWordIsMarkedEverywhere() = runBlocking {
        val hit = search.search("الله").hits.first { it.ayah == AyahRef(1, 1) }

        assertEquals(listOf("ٱللَّهِ"), hit.matches.map { hit.text.substring(it) })
    }

    @Test
    fun aLatinQueryFindsTheTranslationIgnoringCase() = runBlocking {
        val results = search.search("LORD of the worlds")

        assertEquals(listOf(AyahRef(1, 2)), results.hits.map { it.ayah })
        assertEquals(saheeh, results.hits.single().source)
        val hit = results.hits.single()
        assertEquals("Lord of the worlds", hit.text.substring(hit.matches.single()))
    }

    @Test
    fun hitsComeInQuranOrderAndTheCountIsTheNumberOfAyahs() = runBlocking {
        val results = search.search("allah")

        assertEquals(
            listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(2, 255)),
            results.hits.map { it.ayah },
        )
        assertEquals(3, results.count)
    }

    @Test
    fun anAyahThatMatchesInTwoTextsIsListedOnceWithTheArabicPreferred() = runBlocking {
        val both =
            TextSearch(
                loadTexts = {
                    listOf(
                        SearchableTexts(SearchSource.Arabic, listOf(AyahRef(1, 1)), listOf("الله")),
                        SearchableTexts(saheeh, listOf(AyahRef(1, 1)), listOf("Allah")),
                    )
                },
            )

        val arabicQuery = both.search("الله")
        val latinQuery = both.search("allah")

        assertEquals(SearchSource.Arabic, arabicQuery.hits.single().source)
        assertEquals(saheeh, latinQuery.hits.single().source)
    }

    @Test
    fun aQueryOfOneLetterReturnsNothing() = runBlocking {
        assertTrue(search.search("a").hits.isEmpty())
        assertTrue(search.search("  ").hits.isEmpty())
        assertTrue(search.search("ٱَ").hits.isEmpty())
    }

    @Test
    fun aQueryWithNoMatchReturnsAnEmptyList() = runBlocking {
        val results = search.search("zebra")

        assertEquals(0, results.count)
        assertEquals("zebra", results.query)
    }

    @Test
    fun theTextsAreLoadedOnceNoMatterHowManyQueriesRun() = runBlocking {
        search.search("allah")
        search.search("الله")
        search.search("praise")

        assertEquals(1, loadCount)
    }
}
