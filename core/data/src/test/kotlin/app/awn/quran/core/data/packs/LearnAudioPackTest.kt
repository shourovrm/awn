@file:Suppress("MaxLineLength") // Inline JSON fixtures.

package app.awn.quran.core.data.packs

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.MapBundledAssets
import app.awn.quran.core.data.manifestEntry
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Learn sounds: a zip of Opus clips, one folder per unit, checked like the font zips. */
@RunWith(RobolectricTestRunner::class)
class LearnAudioPackTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var server: HttpServer
    private val servedFiles = mutableMapOf<String, ByteArray>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = servedFiles[exchange.requestURI.path.removePrefix("/")]
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun zipOf(vararg members: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in members) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun repositoryServing(archiveBytes: ByteArray, memberCount: Int): FilePackRepository {
        val archive =
            temporaryFolder.newFile("qaida-audio.zip").also { it.writeBytes(archiveBytes) }
        val scripts =
            """{"format_version":1,"packs":[
              |${manifestEntry("learn-qaida-audio", "learn-audio", archive, "learn/qaida-audio.zip", extraFields = ""","member_count":$memberCount""")}]}"""
                .trimMargin()
        val assets =
            MapBundledAssets(
                mapOf(
                    "scripts.json" to scripts.toByteArray(),
                    "translations.json" to """{"format_version":1,"packs":[]}""".toByteArray(),
                    "reciters.json" to """{"format_version":1,"reciters":[]}""".toByteArray(),
                ),
            )
        servedFiles["qaida-audio.zip"] = archiveBytes
        val catalog =
            ManifestCatalog(assets, releaseBaseUrl = "http://127.0.0.1:${server.address.port}")
        val storage = PackStorage(temporaryFolder.newFolder("files"), assets, catalog)
        return FilePackRepository(catalog, storage, ReadOnlyDatabases())
    }

    private fun installPack(repository: FilePackRepository) = runBlocking {
        val entry = repository.manifest("learn-audio").single()
        repository.install(entry) {}
    }

    @Test
    fun theManifestEntryIsAListedSoundPackAndNotAScript() {
        val repository =
            repositoryServing(zipOf("qaida-01/002.opus" to "a", "qaida-01/003.opus" to "b"), 2)

        val items = runBlocking { repository.catalogue() }

        assertEquals(listOf("learn-qaida-audio"), items.map { it.id })
        assertEquals(CatalogueGroup.LearnAudio, items.single().group)
        assertTrue(runBlocking { repository.manifest("scripts") }.isEmpty())
    }

    @Test
    fun anInstalledPackGivesTheFileOfEachClip() {
        val repository =
            repositoryServing(
                zipOf("qaida-01/002.opus" to "alif", "qaida-08a/003.opus" to "madd"),
                2,
            )
        assertNull(repository.learnAudioFile("qaida-01/002"))

        installPack(repository)

        assertEquals("alif", repository.learnAudioFile("qaida-01/002")?.readText())
        assertEquals("madd", repository.learnAudioFile("qaida-08a/003")?.readText())
        assertNull(repository.learnAudioFile("qaida-01/999"))
        assertNull(repository.learnAudioFile("../../etc/passwd"))
        val installed = runBlocking { repository.catalogue() }.single()
        assertTrue(installed.isInstalled)
        assertEquals(8L, installed.installedBytes)
    }

    @Test
    fun aMemberWithAnotherNameIsRefusedAndNothingIsInstalled() {
        val repository =
            repositoryServing(zipOf("qaida-01/002.opus" to "a", "../evil.opus" to "x"), 2)

        val error = assertThrows(PackInstallException::class.java) { installPack(repository) }

        assertTrue(error.message, error.message!!.contains("unexpected member"))
        assertNull(repository.learnAudioFile("qaida-01/002"))
    }

    @Test
    fun anArchiveWithTheWrongNumberOfClipsIsRefused() {
        val repository = repositoryServing(zipOf("qaida-01/002.opus" to "a"), 2)

        val error = assertThrows(PackInstallException::class.java) { installPack(repository) }

        assertTrue(error.message, error.message!!.contains("manifest lists 2"))
        assertFalse(runBlocking { repository.catalogue() }.single().isInstalled)
    }

    @Test
    fun removingThePackDeletesTheClips() {
        val repository = repositoryServing(zipOf("qaida-01/002.opus" to "a"), 1)
        installPack(repository)
        assertNotNull(repository.learnAudioFile("qaida-01/002"))

        runBlocking { repository.remove("learn-qaida-audio") }

        assertNull(repository.learnAudioFile("qaida-01/002"))
    }

    @Test
    fun theSizeOfTheClipsCountsEveryFolder() {
        val repository =
            repositoryServing(zipOf("qaida-01/002.opus" to "aa", "qaida-04/012.opus" to "bbb"), 2)
        installPack(repository)

        assertEquals(5L, runBlocking { repository.sizeOf("learn-qaida-audio") }?.installedBytes)
    }
}
