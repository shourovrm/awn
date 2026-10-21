package app.awn.quran.core.data.reciters

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.DirectoryBundledAssets
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.createPackDatabase
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import app.awn.quran.core.data.realBundledPacksDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PackReciterRepositoryTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val assets = DirectoryBundledAssets(realBundledPacksDirectory)
    private val catalog = ManifestCatalog(assets)

    private fun repository(): Pair<PackReciterRepository, PackStorage> {
        val storage = PackStorage(temporaryFolder.newFolder(), assets, catalog)
        return PackReciterRepository(catalog, storage, ReadOnlyDatabases()) to storage
    }

    @Test
    fun listsAllSixReciters() = runBlocking {
        val reciters = repository().first.reciters()

        assertEquals(
            listOf("alafasy", "husary", "husary_muallim", "minshawi", "basfar", "raad_al_kurdi"),
            reciters.map { it.id },
        )
        assertEquals("alafasy", reciters.first().timingPack)
        assertNull(reciters.last().ayahUrlPattern)
        assertNull(reciters.last().timingPack)
    }

    @Test
    fun onlyReciterWithWordTimingsOffersWordAudio() = runBlocking {
        val reciters = repository().first.reciters().associateBy { it.id }

        // The word card needs both the word flag and a timing pack to play a word.
        assertTrue(reciters.getValue("minshawi").wordByWord)
        assertEquals("minshawi", reciters.getValue("minshawi").timingPack)
        // Basfar has a timing pack that marks ayahs only; it cannot play a single word.
        assertEquals(false, reciters.getValue("basfar").wordByWord)
        assertEquals("basfar", reciters.getValue("basfar").timingPack)
        assertEquals(false, reciters.getValue("raad_al_kurdi").wordByWord)
    }

    @Test
    fun buildsTheEveryAyahUrlFromTheManifestPattern() = runBlocking {
        val audio = repository().first.ayahAudio("alafasy", AyahRef(2, 255))

        assertEquals(
            "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/002255.mp3",
            audio!!.url,
        )
    }

    @Test
    fun withoutTheTimingPackThereAreNoWordTimings() = runBlocking {
        val audio = repository().first.ayahAudio("alafasy", AyahRef(1, 1))!!

        assertEquals(emptyList<WordTiming>(), audio.words)
        assertNull(audio.durationMs)
    }

    @Test
    fun installedTimingPackAddsDurationAndWordTimings() = runBlocking {
        val (repository, storage) = repository()
        createPackDatabase(
            storage.root.resolve("alafasy.sqlite"),
            1,
            "CREATE TABLE segment (surah INTEGER, ayah INTEGER, position INTEGER, start_ms INTEGER, end_ms INTEGER)",
            "CREATE TABLE ayah_audio (surah INTEGER, ayah INTEGER, url TEXT, duration_ms INTEGER, offset_ms INTEGER)",
            "INSERT INTO ayah_audio VALUES (1, 1, 'x', 4200, 0)",
            "INSERT INTO segment VALUES (1, 1, 2, 900, 1700)",
            "INSERT INTO segment VALUES (1, 1, 1, 0, 900)",
        )

        val audio = repository.ayahAudio("alafasy", AyahRef(1, 1))!!

        assertEquals(4200, audio.durationMs)
        assertEquals(listOf(WordTiming(1, 0, 900), WordTiming(2, 900, 1700)), audio.words)
    }

    @Test
    fun ayahMissingFromTheTimingPackKeepsTheUrlWithoutTimings() = runBlocking {
        val (repository, storage) = repository()
        createPackDatabase(
            storage.root.resolve("alafasy.sqlite"),
            1,
            "CREATE TABLE segment (surah INTEGER, ayah INTEGER, position INTEGER, start_ms INTEGER, end_ms INTEGER)",
            "CREATE TABLE ayah_audio (surah INTEGER, ayah INTEGER, url TEXT, duration_ms INTEGER, offset_ms INTEGER)",
        )

        val audio = repository.ayahAudio("alafasy", AyahRef(1, 1))!!

        assertTrue(audio.url.endsWith("001001.mp3"))
        assertTrue(audio.words.isEmpty())
    }

    @Test
    fun unknownReciterAndReciterWithoutAyahFilesGiveNull() = runBlocking {
        val repository = repository().first

        assertNull(repository.ayahAudio("nobody", AyahRef(1, 1)))
        assertNull(repository.ayahAudio("raad_al_kurdi", AyahRef(1, 1)))
    }
}
