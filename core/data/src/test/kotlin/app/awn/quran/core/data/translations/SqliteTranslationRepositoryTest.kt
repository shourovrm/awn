@file:Suppress("MaxLineLength") // Inline SQL and JSON fixtures.

package app.awn.quran.core.data.translations

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.DirectoryBundledAssets
import app.awn.quran.core.data.MapBundledAssets
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.createPackDatabase
import app.awn.quran.core.data.manifestEntry
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import app.awn.quran.core.data.realBundledPacksDirectory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SqliteTranslationRepositoryTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun realRepository(): SqliteTranslationRepository {
        val assets = DirectoryBundledAssets(realBundledPacksDirectory)
        val catalog = ManifestCatalog(assets)
        val storage = PackStorage(temporaryFolder.newFolder(), assets, catalog)
        return SqliteTranslationRepository(catalog, storage, ReadOnlyDatabases())
    }

    @Test
    fun bundledTranslationsAreInstalledAndTheRestAreNot() = runBlocking {
        val available = realRepository().available().associateBy { it.id.id }

        assertEquals(
            setOf(
                "en_saheeh",
                "en_abdul_haleem",
                "en_transliteration",
                "bn_zakaria",
                "bn_quantum",
                "en_asad",
            ),
            available.keys,
        )
        assertTrue(available.getValue("en_saheeh").installed)
        assertTrue(available.getValue("bn_zakaria").installed)
        assertTrue(available.getValue("en_transliteration").installed)
        assertFalse(available.getValue("en_abdul_haleem").installed)
        assertFalse(available.getValue("bn_quantum").installed)
        assertFalse(available.getValue("en_asad").installed)
        assertEquals("bn", available.getValue("bn_zakaria").language)
    }

    @Test
    fun readsTheFirstAyahOfTheBundledSaheehPack() = runBlocking {
        val passage = realRepository().text(TranslationId("en_saheeh"), AyahRef(1, 1))

        assertNotNull(passage)
        assertEquals(1, passage!!.ayahFrom)
        assertEquals(1, passage.ayahTo)
        assertTrue(passage.text.startsWith("In the name of"))
    }

    @Test
    fun translationThatIsNotInstalledIsNull() = runBlocking {
        assertNull(realRepository().text(TranslationId("en_abdul_haleem"), AyahRef(1, 1)))
        assertNull(realRepository().text(TranslationId("no_such_pack"), AyahRef(1, 1)))
    }

    @Test
    fun ayahWithoutARowIsNull() = runBlocking {
        assertNull(realRepository().text(TranslationId("en_saheeh"), AyahRef(1, 8)))
    }

    @Test
    fun wordByWordHasOneMeaningPerSpokenWord() = runBlocking {
        val repository = realRepository()

        assertEquals(4, repository.wordByWord("en", AyahRef(1, 1)).size)
        assertEquals(4, repository.wordByWord("bn", AyahRef(1, 1)).size)
        assertEquals(emptyList<String>(), repository.wordByWord("fr", AyahRef(1, 1)))
    }

    @Test
    fun englishPhraseIsShownOnceInTheBundledPack() = runBlocking {
        val meanings = realRepository().wordByWord("en", AyahRef(9, 3))

        assertEquals("from Allah", meanings[1])
        assertEquals("", meanings[2])
        assertEquals("(of) the greater Pilgrimage", meanings[7])
        assertEquals("", meanings[8])
    }

    @Test
    fun englishMeaningsHaveNoRepeatedNeighboursExceptTheGenuineOne() = runBlocking {
        val repository = realRepository()
        val repeats = mutableListOf<String>()
        for (surah in listOf(4, 9, 10, 58)) {
            for (ayahNumber in 1..20) {
                val meanings = repository.wordByWord("en", AyahRef(surah, ayahNumber))
                meanings.zipWithNext().forEachIndexed { index, (left, right) ->
                    if (left == right && left.isNotEmpty())
                        repeats += "$surah:$ayahNumber:${index + 2}"
                }
            }
        }
        assertEquals(emptyList<String>(), repeats)
    }

    @Test
    fun mergedRangeReturnsTheSameTextForEveryAyahOfTheRange() = runBlocking {
        val packFile = temporaryFolder.newFile("merged.sqlite").also { it.delete() }
        createPackDatabase(
            packFile,
            1,
            "CREATE TABLE translation (surah INTEGER, ayah INTEGER, ayah_from INTEGER, ayah_to INTEGER, text TEXT, PRIMARY KEY (surah, ayah))",
            "INSERT INTO translation VALUES (2, 1, 1, 2, 'Passage for one and two')",
            "INSERT INTO translation VALUES (2, 2, 1, 2, 'Passage for one and two')",
            "INSERT INTO translation VALUES (2, 3, 3, 3, 'Passage for three')",
        )
        val manifest =
            """{"format_version":1,"packs":[${manifestEntry("merged", "ayah", packFile, "translations/merged.sqlite", extraFields = ""","language":"en"""")}]}"""
        val assets =
            MapBundledAssets(
                mapOf(
                    "translations.json" to manifest.toByteArray(),
                    "scripts.json" to """{"format_version":1,"packs":[]}""".toByteArray(),
                    "reciters.json" to """{"format_version":1,"reciters":[]}""".toByteArray(),
                    "merged.sqlite" to packFile.readBytes(),
                ),
            )
        val catalog = ManifestCatalog(assets)
        val storage = PackStorage(temporaryFolder.newFolder(), assets, catalog)
        val repository = SqliteTranslationRepository(catalog, storage, ReadOnlyDatabases())

        val first = repository.text(TranslationId("merged"), AyahRef(2, 1))!!
        val second = repository.text(TranslationId("merged"), AyahRef(2, 2))!!
        val third = repository.text(TranslationId("merged"), AyahRef(2, 3))!!

        assertEquals(1, first.ayahFrom)
        assertEquals(2, first.ayahTo)
        assertEquals(first.text, second.text)
        assertEquals(1, second.ayahFrom)
        assertEquals(3, third.ayahFrom)
        assertEquals(3, third.ayahTo)
        assertTrue(File(storage.root, "merged.sqlite").isFile)
    }
}
