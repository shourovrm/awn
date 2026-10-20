package app.awn.quran.core.data.search

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.DirectoryBundledAssets
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import app.awn.quran.core.data.realBundledPacksDirectory
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs the search over the real bundled packs: 6,236 Arabic ayahs and every installed translation.
 * The printed timings are the measurement behind "plain Kotlin over the ayahs is fast enough".
 */
@RunWith(RobolectricTestRunner::class)
class TextSearchRealPacksTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun realTexts(): SqliteSearchTexts {
        val assets = DirectoryBundledAssets(realBundledPacksDirectory)
        val catalog = ManifestCatalog(assets)
        val storage = PackStorage(temporaryFolder.newFolder(), assets, catalog)
        return SqliteSearchTexts(
            quranDatabaseFile = {
                File(realBundledPacksDirectory, "quran.sqlite")
            },
            catalog = catalog,
            storage = storage,
            databases = ReadOnlyDatabases(),
        )
    }

    @Test
    fun loadsEveryAyahOfTheArabicAndOfEachInstalledTranslation() = runBlocking {
        val texts = realTexts().load()

        assertEquals(SearchSource.Arabic, texts.first().source)
        assertEquals(6236, texts.first().ayahs.size)
        assertTrue("translations installed: ${texts.size - 1}", texts.size >= 2)
    }

    @Test
    fun findsKnownAyahsAndStaysFast() = runBlocking {
        val texts = realTexts()
        var search: TextSearch? = null
        val loadMillis = measureTimeMillis {
            search = TextSearch(texts::load).also { it.search("ab") }
        }
        val engine = checkNotNull(search)

        val queries =
            listOf("الرحمن", "ٱلصَّبْرِ", "الصلاة", "mercy", "patience", "ar-rahman", "ال")
        val timings = queries.map { query ->
            var count = 0
            val millis = measureTimeMillis { count = engine.search(query).count }
            println("search '$query': $count ayahs in $millis ms")
            millis
        }
        println("first query including loading and indexing: $loadMillis ms")

        val patience = engine.search("الصبر").hits.map { it.ayah }
        assertTrue("2:153 mentions patience", AyahRef(2, 153) in patience)
        assertTrue("mercy appears in over 100 ayahs", engine.search("mercy").count > 100)
        assertTrue("slowest query ${timings.max()} ms", timings.max() < SLOWEST_QUERY_MILLIS)
        assertTrue("first query $loadMillis ms", loadMillis < FIRST_QUERY_MILLIS)
    }

    private companion object {
        const val SLOWEST_QUERY_MILLIS = 400L
        const val FIRST_QUERY_MILLIS = 8_000L
    }
}
