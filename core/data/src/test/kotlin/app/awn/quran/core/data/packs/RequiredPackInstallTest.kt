@file:Suppress("MaxLineLength") // Inline JSON fixtures.

package app.awn.quran.core.data.packs

import app.awn.quran.core.data.MapBundledAssets
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.createPackDatabase
import app.awn.quran.core.data.manifestEntry
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.first
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

/**
 * The IndoPak prints: two font entries that share one layout pack, and whose font comes from the
 * host in `download_url` instead of the data release. A local server stands in for both hosts.
 */
@RunWith(RobolectricTestRunner::class)
class RequiredPackInstallTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var server: HttpServer
    private val servedFiles = mutableMapOf<String, ByteArray>()
    private val requestedPaths = mutableListOf<String>()
    private lateinit var repository: FilePackRepository
    private lateinit var storage: PackStorage
    private lateinit var catalog: ManifestCatalog

    private val fontBytes = "indopak-font-bytes".toByteArray()

    @Before
    fun startServerAndBuildWorld() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path.removePrefix("/")
            requestedPaths += path
            val body = servedFiles[path]
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        buildWorld()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun buildWorld() {
        val quranText = temporaryFolder.newFile("quran.sqlite").also { it.delete() }
        createPackDatabase(quranText, 1)
        val layouts = temporaryFolder.newFile("indopak.sqlite").also { it.delete() }
        createPackDatabase(layouts, 1, "CREATE TABLE word (surah INTEGER)")
        val font = temporaryFolder.newFile("indopak-font.ttf").also { it.writeBytes(fontBytes) }
        val externalUrl = "http://127.0.0.1:${server.address.port}/external/indopak-font.ttf"
        val fontFields = ""","requires":["indopak-layouts"],"download_url":"$externalUrl""""
        val scripts =
            """{"format_version":1,"packs":[
              |${manifestEntry("quran-text", "text", quranText, "quran/quran.sqlite")},
              |${manifestEntry("indopak-layouts", "text", layouts, "quran/indopak.sqlite", extraFields = ""","requires":["quran-text"]""")},
              |${manifestEntry("indopak-15", "font", font, "fonts/indopak/indopak-font.ttf", extraFields = fontFields)},
              |${manifestEntry("indopak-16", "font", font, "fonts/indopak/indopak-font.ttf", extraFields = fontFields)}]}"""
                .trimMargin()
        val assets =
            MapBundledAssets(
                mapOf(
                    "scripts.json" to scripts.toByteArray(),
                    "translations.json" to """{"format_version":1,"packs":[]}""".toByteArray(),
                    "reciters.json" to """{"format_version":1,"reciters":[]}""".toByteArray(),
                    "quran.sqlite" to quranText.readBytes(),
                ),
            )
        servedFiles["indopak.sqlite"] = layouts.readBytes()
        servedFiles["external/indopak-font.ttf"] = fontBytes
        catalog =
            ManifestCatalog(
                assets,
                releaseBaseUrl = "http://127.0.0.1:${server.address.port}",
                externalUrlPrefix = "http://",
            )
        storage = PackStorage(temporaryFolder.newFolder("files"), assets, catalog)
        repository = FilePackRepository(catalog, storage, ReadOnlyDatabases())
    }

    private suspend fun entryOf(id: String) = repository.catalogue().first { it.id == id }.entry

    private suspend fun rowOf(id: String) = repository.catalogue().first { it.id == id }

    @Test
    fun theFontEntryDownloadsFromItsOwnAddressAndTheLayoutsFromTheRelease() = runBlocking {
        assertTrue(entryOf("indopak-15").downloadUrl.endsWith("/external/indopak-font.ttf"))
        assertTrue(
            catalog.findPack("indopak-layouts")!!.entry.downloadUrl.endsWith("/indopak.sqlite"),
        )
    }

    @Test
    fun installingAPrintAlsoInstallsItsLayoutsAndChecksTheFontByHash() = runBlocking {
        repository.install(entryOf("indopak-15")) {}

        assertEquals(
            fontBytes.toList(),
            repository.fontFile(ScriptId.IndoPak15, 1)!!.readBytes().toList(),
        )
        assertEquals(listOf("indopak.sqlite", "external/indopak-font.ttf"), requestedPaths)
        val installedIds = repository.installed().first().map { it.id }
        assertTrue("indopak-layouts" in installedIds)
        assertTrue("indopak-15" in installedIds)
    }

    @Test
    fun theFirstPrintShowsTheSizeOfBothFilesAndTheSecondOnlyItsFont() = runBlocking {
        val layoutBytes = servedFiles.getValue("indopak.sqlite").size.toLong()
        assertEquals(fontBytes.size + layoutBytes, rowOf("indopak-15").downloadBytes)

        repository.install(entryOf("indopak-15")) {}

        assertEquals(fontBytes.size.toLong(), rowOf("indopak-16").downloadBytes)
        assertFalse(rowOf("indopak-16").isInstalled)
        assertTrue(rowOf("indopak-15").isInstalled)
    }

    @Test
    fun theLayoutsStayWhileAnotherPrintNeedsThemAndGoWithTheLast() = runBlocking {
        repository.install(entryOf("indopak-15")) {}
        repository.install(entryOf("indopak-16")) {}

        repository.remove("indopak-15")
        assertTrue("indopak-layouts" in repository.installed().first().map { it.id })
        assertNull(repository.fontFile(ScriptId.IndoPak15, 1))
        assertNotNull(repository.fontFile(ScriptId.IndoPak16, 1))

        repository.remove("indopak-16")
        assertFalse("indopak-layouts" in repository.installed().first().map { it.id })
    }

    @Test
    fun aFontWhoseHashDiffersFromTheManifestIsDiscarded() = runBlocking {
        servedFiles["external/indopak-font.ttf"] = "indopak-font-BYTES".toByteArray()

        val failure =
            assertThrows(PackInstallException::class.java) {
                runBlocking { repository.install(entryOf("indopak-15")) {} }
            }

        assertEquals(PackFailureReason.Damaged, failure.reason)
        assertNull(repository.fontFile(ScriptId.IndoPak15, 1))
        assertEquals(0L, storage.partialDownloadBytes(catalog.findPack("indopak-15")!!))
    }

    @Test
    fun aDownloadAddressThatIsNotHttpsIsRefused() {
        val file: File = temporaryFolder.newFile("any.ttf").also { it.writeBytes(fontBytes) }
        val scripts =
            """{"format_version":1,"packs":[
              |${manifestEntry("indopak-15", "font", file, "fonts/any.ttf", extraFields = ""","download_url":"http://example.org/any.ttf"""")}]}"""
                .trimMargin()
        val assets =
            MapBundledAssets(
                mapOf(
                    "scripts.json" to scripts.toByteArray(),
                    "translations.json" to """{"format_version":1,"packs":[]}""".toByteArray(),
                    "reciters.json" to """{"format_version":1,"reciters":[]}""".toByteArray(),
                ),
            )

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                ManifestCatalog(assets).scriptPacks
            }

        assertTrue(error.message.orEmpty().contains("https://"))
    }
}
