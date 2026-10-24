package app.awn.quran.core.data.search

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A query with a typo found nothing. When the exact search is empty and the query has five letters
 * or more, [TextSearch] retries with one letter of difference per word and marks the results.
 */
class CloseMatchSearchTest {

    private val saheeh = SearchSource.Translation(TranslationId("en_saheeh"))

    private val english =
        SearchableTexts(
            saheeh,
            listOf(AyahRef(2, 105), AyahRef(2, 153), AyahRef(2, 178)),
            listOf(
                "And Allah selects for His mercy whom He wills.",
                "O you who have believed, seek help through patience and prayer.",
                "That is an alleviation from your Lord and a mercy.",
            ),
        )

    private val arabic =
        SearchableTexts(
            SearchSource.Arabic,
            listOf(AyahRef(1, 1), AyahRef(1, 2)),
            listOf(
                "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
            ),
        )

    private val search = TextSearch(loadTexts = { listOf(arabic, english) })

    @Test
    fun aMistypedWordFindsTheAyahsWithTheRealWord() = runBlocking {
        val results = search.search("mercyy")

        assertTrue(results.isCloseMatch)
        assertEquals(listOf(AyahRef(2, 105), AyahRef(2, 178)), results.hits.map { it.ayah })
    }

    @Test
    fun theCloseMatchIsMarkedOnTheWholeWord() = runBlocking {
        val hit = search.search("mercyy").hits.first()

        assertEquals("mercy", hit.text.substring(hit.matches.single()))
    }

    @Test
    fun anArabicTypoFindsTheAyah() = runBlocking {
        val results = search.search("الحمدد")

        assertTrue(results.isCloseMatch)
        assertEquals(listOf(AyahRef(1, 2)), results.hits.map { it.ayah })
    }

    @Test
    fun anExactHitIsNeverMixedWithCloseMatches() = runBlocking {
        val results = search.search("patience")

        assertFalse(results.isCloseMatch)
        assertEquals(listOf(AyahRef(2, 153)), results.hits.map { it.ayah })
    }

    @Test
    fun aQueryOfFourLettersGetsNoCloseMatches() = runBlocking {
        val results = search.search("merk")

        assertFalse(results.isCloseMatch)
        assertTrue(results.hits.isEmpty())
    }

    @Test
    fun twoLettersOffInOneWordIsNotCloseEnough() = runBlocking {
        assertTrue(search.search("mercuu").hits.isEmpty())
    }

    @Test
    fun everyWordOfAPhraseMayDifferByOneLetter() = runBlocking {
        val results = search.search("your lord and a marcy")

        assertEquals(listOf(AyahRef(2, 178)), results.hits.map { it.ayah })
    }
}
