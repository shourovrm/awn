package app.awn.quran.core.data.reciters

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.DirectoryBundledAssets
import app.awn.quran.core.data.MapBundledAssets
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.createPackDatabase
import app.awn.quran.core.data.packs.ManifestCatalog
import app.awn.quran.core.data.packs.PackStorage
import app.awn.quran.core.data.packs.ReadOnlyDatabases
import app.awn.quran.core.data.realBundledPacksDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun listsAllFiveReciters() = runBlocking {
        val reciters = repository().first.reciters()

        assertEquals(
            listOf("alafasy", "husary", "husary_muallim", "minshawi", "basfar"),
            reciters.map { it.id },
        )
        assertEquals("alafasy", reciters.first().timingPack)
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
    fun unknownReciterGivesNull() = runBlocking {
        val repository = repository().first

        assertNull(repository.ayahAudio("nobody", AyahRef(1, 1)))
    }

    /**
     * A synthetic manifest with three reciters: one whole-surah reciter with a timing pack, one
     * per-ayah reciter with a timing pack, and one whole-surah reciter with no timing pack.
     */
    private fun wholeSurahRepository(): Pair<PackReciterRepository, PackStorage> {
        val reciters =
            """
            {"format_version":1,"reciters":[
              {"id":"wholesurah","name":"Whole surah","surah_url_pattern":"https://example.org/{SSS}.mp3",
               "ayah_url_pattern":null,"word_by_word":true,"timing_pack":"wholesurah",
               "timing_granularity":"word","format_version":1,"file":"timings/wholesurah.sqlite",
               "sha256":"0000000000000000000000000000000000000000000000000000000000000000","bytes":1,"credit":"Credit","source_url":"https://example.org"},
              {"id":"perayah","name":"Per ayah","surah_url_pattern":"https://example.org/{SSS}.mp3",
               "ayah_url_pattern":"https://example.org/perayah/{SSS}{AAA}.mp3","word_by_word":true,
               "timing_pack":"perayah","timing_granularity":"word","format_version":1,
               "file":"timings/perayah.sqlite","sha256":"0000000000000000000000000000000000000000000000000000000000000000","bytes":1,"credit":"Credit",
               "source_url":"https://example.org"},
              {"id":"surahonly","name":"Surah only","surah_url_pattern":"https://example.org/{SSS}.mp3",
               "ayah_url_pattern":null,"word_by_word":false,"timing_pack":null,"format_version":1}
            ]}
            """
                .trimIndent()
        val assets =
            MapBundledAssets(
                mapOf(
                    "reciters.json" to reciters.toByteArray(),
                    "scripts.json" to """{"format_version":1,"packs":[]}""".toByteArray(),
                    "translations.json" to """{"format_version":1,"packs":[]}""".toByteArray(),
                ),
            )
        val catalog = ManifestCatalog(assets)
        val storage = PackStorage(temporaryFolder.newFolder(), assets, catalog)
        return PackReciterRepository(catalog, storage, ReadOnlyDatabases()) to storage
    }

    private fun createWholeSurahPack(storage: PackStorage, vararg statements: String) {
        createPackDatabase(
            storage.root.resolve("wholesurah.sqlite"),
            1,
            "CREATE TABLE segment (surah INTEGER, ayah INTEGER, position INTEGER, start_ms INTEGER, end_ms INTEGER)",
            "CREATE TABLE ayah_audio (surah INTEGER, ayah INTEGER, url TEXT, duration_ms INTEGER, offset_ms INTEGER)",
            "CREATE TABLE ayah_bytes (surah INTEGER, ayah INTEGER, start_byte INTEGER, " +
                "end_byte INTEGER, start_byte_ms INTEGER)",
            *statements,
        )
    }

    @Test
    fun installedPackGivesTheSurahFileAndWhereTheAyahLiesInIt() = runBlocking {
        val (repository, storage) = wholeSurahRepository()
        createWholeSurahPack(
            storage,
            "INSERT INTO ayah_audio VALUES (2, 3, 'https://example.org/002.mp3', 5000, 61000)",
            "INSERT INTO ayah_bytes VALUES (2, 3, 40000, 52000, 60500)",
            "INSERT INTO segment VALUES (2, 3, 1, 0, 900)",
            "INSERT INTO segment VALUES (2, 3, 2, 900, 1700)",
        )

        val audio = repository.ayahAudio("wholesurah", AyahRef(2, 3))!!

        assertEquals("https://example.org/002.mp3", audio.url)
        assertTrue(audio.isInWholeSurahFile)
        assertEquals(61000, audio.startMs)
        assertEquals(66000, audio.durationMs)
        assertEquals(listOf(WordTiming(1, 61000, 61900), WordTiming(2, 61900, 62700)), audio.words)
        assertEquals(40000L, audio.startByte)
        assertEquals(52000L, audio.endByte)
        assertEquals(60500, audio.startByteMs)
    }

    @Test
    fun lastAyahWithoutADurationRunsToTheEndOfTheSurahFile() = runBlocking {
        val (repository, storage) = wholeSurahRepository()
        createWholeSurahPack(
            storage,
            "INSERT INTO ayah_audio VALUES (2, 4, 'https://example.org/002.mp3', NULL, 66000)",
            "INSERT INTO ayah_bytes VALUES (2, 4, 52000, NULL, 66000)",
        )

        val audio = repository.ayahAudio("wholesurah", AyahRef(2, 4))!!

        assertEquals(66000, audio.startMs)
        assertNull(audio.durationMs)
        assertNull(audio.endByte)
    }

    @Test
    fun packWithoutTheByteTableGivesNullByteFieldsAndKeepsTheTimings() = runBlocking {
        val (repository, storage) = wholeSurahRepository()
        createPackDatabase(
            storage.root.resolve("wholesurah.sqlite"),
            1,
            "CREATE TABLE segment (surah INTEGER, ayah INTEGER, position INTEGER, start_ms INTEGER, end_ms INTEGER)",
            "CREATE TABLE ayah_audio (surah INTEGER, ayah INTEGER, url TEXT, duration_ms INTEGER, offset_ms INTEGER)",
            "INSERT INTO ayah_audio VALUES (2, 3, 'https://example.org/002.mp3', 5000, 61000)",
        )

        val audio = repository.ayahAudio("wholesurah", AyahRef(2, 3))!!

        assertEquals(61000, audio.startMs)
        assertNull(audio.startByte)
        assertNull(audio.endByte)
        assertNull(audio.startByteMs)
    }

    @Test
    fun ayahWithoutAByteRowGivesNullByteFieldsAndKeepsTheTimings() = runBlocking {
        val (repository, storage) = wholeSurahRepository()
        createWholeSurahPack(
            storage,
            "INSERT INTO ayah_audio VALUES (2, 5, 'https://example.org/002.mp3', 3000, 70000)",
        )

        val audio = repository.ayahAudio("wholesurah", AyahRef(2, 5))!!

        assertEquals(70000, audio.startMs)
        assertNull(audio.startByte)
        assertNull(audio.endByte)
        assertNull(audio.startByteMs)
    }

    @Test
    fun wholeSurahReciterWithoutInstalledPackGivesNull() = runBlocking {
        val repository = wholeSurahRepository().first

        assertNull(repository.ayahAudio("wholesurah", AyahRef(2, 3)))
    }

    @Test
    fun wholeSurahReciterWithPackButNoRowForTheAyahGivesNull() = runBlocking {
        val (repository, storage) = wholeSurahRepository()
        createWholeSurahPack(storage)

        assertNull(repository.ayahAudio("wholesurah", AyahRef(2, 3)))
    }

    @Test
    fun reciterWithoutAyahFilesAndWithoutTimingPackGivesNull() = runBlocking {
        val repository = wholeSurahRepository().first

        assertNull(repository.ayahAudio("surahonly", AyahRef(2, 3)))
    }

    @Test
    fun perAyahReciterKeepsItsOwnFileAndStartsAtZero() = runBlocking {
        val (repository, storage) = wholeSurahRepository()
        createPackDatabase(
            storage.root.resolve("perayah.sqlite"),
            1,
            "CREATE TABLE segment (surah INTEGER, ayah INTEGER, position INTEGER, start_ms INTEGER, end_ms INTEGER)",
            "CREATE TABLE ayah_audio (surah INTEGER, ayah INTEGER, url TEXT, duration_ms INTEGER, offset_ms INTEGER)",
            "INSERT INTO ayah_audio VALUES (2, 3, 'ignored.mp3', 4200, 0)",
            "INSERT INTO segment VALUES (2, 3, 1, 0, 900)",
        )

        val audio = repository.ayahAudio("perayah", AyahRef(2, 3))!!

        assertEquals("https://example.org/perayah/002003.mp3", audio.url)
        assertEquals(0, audio.startMs)
        assertFalse(audio.isInWholeSurahFile)
        assertEquals(4200, audio.durationMs)
        assertEquals(listOf(WordTiming(1, 0, 900)), audio.words)
        assertNull(audio.startByte)
        assertNull(audio.endByte)
        assertNull(audio.startByteMs)
    }
}
