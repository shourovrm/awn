@file:Suppress("MaxLineLength") // Inline SQL and JSON fixtures.

package app.awn.quran.core.data.packs

import app.awn.quran.core.data.MapBundledAssets
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.createPackDatabase
import app.awn.quran.core.data.manifestEntry
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

/** Downloads from a local HTTP server that implements Range requests. */
@RunWith(RobolectricTestRunner::class)
class FilePackRepositoryTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var server: HttpServer
    private val servedFiles = mutableMapOf<String, ByteArray>()
    private val rangeHeadersSeen = mutableListOf<String?>()
    private var serverHonoursRange = true
    private lateinit var repository: FilePackRepository
    private lateinit var storage: PackStorage
    private lateinit var catalog: ManifestCatalog

    private val fontArchive = zipOf("p1.ttf" to "one", "p2.ttf" to "two", "p3.ttf" to "three")
    private val v1Fonts = zipOf("p1.ttf" to "v1-one", "p2.ttf" to "v1-two", "p3.ttf" to "v1-three")
    private val v4Fonts = zipOf("p1.ttf" to "v4-one", "p2.ttf" to "v4-two", "p3.ttf" to "v4-three")
    private val hafsFont = "hafs-font-bytes".toByteArray()

    @Before
    fun startServerAndBuildWorld() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = servedFiles[exchange.requestURI.path.removePrefix("/")]
            val range = exchange.requestHeaders.getFirst("Range")
            rangeHeadersSeen += range
            when {
                body == null -> exchange.sendResponseHeaders(404, -1)
                range != null && serverHonoursRange -> {
                    val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    if (start >= body.size) {
                        exchange.sendResponseHeaders(416, -1)
                    } else {
                        exchange.responseHeaders.add(
                            "Content-Range",
                            "bytes $start-${body.size - 1}/${body.size}",
                        )
                        exchange.sendResponseHeaders(206, (body.size - start).toLong())
                        exchange.responseBody.use { it.write(body, start, body.size - start) }
                    }
                }
                else -> {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    /** Builds the catalog, storage and repository around the synthetic packs in [packFiles]. */
    private fun buildWorld(packFiles: Map<String, File>) {
        val hafs = temporaryFolder.newFile("hafs.ttf").also { it.writeBytes(hafsFont) }
        val archive = temporaryFolder.newFile("v2.zip").also { it.writeBytes(fontArchive) }
        val v1Archive = temporaryFolder.newFile("v1.zip").also { it.writeBytes(v1Fonts) }
        val v4Archive = temporaryFolder.newFile("v4-tajweed.zip").also { it.writeBytes(v4Fonts) }
        val translation = packFiles.getValue("en_test.sqlite")
        val scripts =
            """{"format_version":1,"packs":[
              |${manifestEntry("qpc-hafs", "font", hafs, "fonts/qpc-hafs/hafs.ttf")},
              |${manifestEntry("v2", "font", archive, "fonts/v2.zip", extraFields = ""","member_count":3""")},
              |${manifestEntry("v1", "font", v1Archive, "fonts/v1.zip", extraFields = ""","member_count":3""")},
              |${manifestEntry("v4-tajweed", "font", v4Archive, "fonts/v4-tajweed.zip", extraFields = ""","member_count":3""")}]}"""
                .trimMargin()
        val translations =
            """{"format_version":1,"packs":[
              |${manifestEntry("en_test", "ayah", translation, "translations/en_test.sqlite", extraFields = ""","language":"en"""")}]}"""
                .trimMargin()
        val assets =
            MapBundledAssets(
                mapOf(
                    "scripts.json" to scripts.toByteArray(),
                    "translations.json" to translations.toByteArray(),
                    "reciters.json" to """{"format_version":1,"reciters":[]}""".toByteArray(),
                    "hafs.ttf" to hafsFont,
                ),
            )
        servedFiles["v2.zip"] = fontArchive
        servedFiles["v1.zip"] = v1Fonts
        servedFiles["v4-tajweed.zip"] = v4Fonts
        servedFiles["en_test.sqlite"] = translation.readBytes()
        catalog =
            ManifestCatalog(assets, releaseBaseUrl = "http://127.0.0.1:${server.address.port}")
        storage = PackStorage(temporaryFolder.newFolder("files"), assets, catalog)
        repository = FilePackRepository(catalog, storage, ReadOnlyDatabases())
    }

    private fun translationPackFile(formatVersion: Int = 1): File {
        val file = temporaryFolder.newFile("en_test.sqlite").also { it.delete() }
        createPackDatabase(
            file,
            formatVersion,
            "CREATE TABLE translation (surah INTEGER, ayah INTEGER, ayah_from INTEGER, ayah_to INTEGER, text TEXT)",
            *(1..400)
                .map { "INSERT INTO translation VALUES (1, $it, $it, $it, 'text number $it')" }
                .toTypedArray(),
        )
        return file
    }

    private fun entry(id: String) = catalog.findPack(id)!!.entry

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

    @Test
    fun manifestsListTheEntriesOfEachKind() {
        runBlocking {
            buildWorld(mapOf("en_test.sqlite" to translationPackFile()))

            assertEquals(
                listOf("qpc-hafs", "v2", "v1", "v4-tajweed"),
                repository.manifest("scripts.json").map { it.id },
            )
            assertEquals(listOf("en_test"), repository.manifest("translations").map { it.id })
            assertEquals(emptyList<Any>(), repository.manifest("reciters"))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.manifest("nope") }
            }
        }
    }

    @Test
    fun installDownloadsVerifiesAndListsADatabasePack() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        val progress = mutableListOf<Float>()

        repository.install(entry("en_test")) { progress += it }

        val installed = repository.installed().first().associateBy { it.id }
        assertEquals("translation", installed.getValue("en_test").kind)
        assertEquals(
            servedFiles.getValue("en_test.sqlite").size.toLong(),
            installed.getValue("en_test").bytes,
        )
        assertEquals(1f, progress.last(), 0f)
        assertEquals(progress.sorted(), progress)
        assertTrue(File(storage.root, "en_test.sqlite").isFile)
        assertFalse(File(storage.downloadsDirectory, "en_test.sqlite.part").exists())
    }

    @Test
    fun interruptedDownloadResumesWithARangeRequest() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        val whole = servedFiles.getValue("en_test.sqlite")
        val partial = File(storage.downloadsDirectory, "en_test.sqlite.part")
        partial.parentFile!!.mkdirs()
        partial.writeBytes(whole.copyOfRange(0, 1000))

        repository.install(entry("en_test")) {}

        assertEquals(listOf<String?>("bytes=1000-"), rangeHeadersSeen)
        assertEquals(whole.toList(), File(storage.root, "en_test.sqlite").readBytes().toList())
    }

    @Test
    fun serverThatIgnoresRangeRestartsTheFileFromTheBeginning() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        serverHonoursRange = false
        val whole = servedFiles.getValue("en_test.sqlite")
        val partial = File(storage.downloadsDirectory, "en_test.sqlite.part")
        partial.parentFile!!.mkdirs()
        partial.writeBytes(ByteArray(500) { 7 })

        repository.install(entry("en_test")) {}

        assertEquals(whole.toList(), File(storage.root, "en_test.sqlite").readBytes().toList())
    }

    @Test
    fun alreadyCompletePartialFileIsVerifiedWithoutRedownload() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        val partial = File(storage.downloadsDirectory, "en_test.sqlite.part")
        partial.parentFile!!.mkdirs()
        partial.writeBytes(servedFiles.getValue("en_test.sqlite"))

        repository.install(entry("en_test")) {}

        assertTrue(File(storage.root, "en_test.sqlite").isFile)
    }

    @Test
    fun checksumMismatchFailsNamingTheFileAndInstallsNothing() {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        servedFiles["en_test.sqlite"] =
            servedFiles.getValue("en_test.sqlite").also { it[100] = (it[100] + 1).toByte() }

        val error =
            assertThrows(PackInstallException::class.java) {
                runBlocking { repository.install(entry("en_test")) {} }
            }

        assertEquals("en_test.sqlite", error.fileName)
        assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
        assertFalse(File(storage.root, "en_test.sqlite").exists())
        assertFalse(File(storage.downloadsDirectory, "en_test.sqlite.part").exists())
    }

    @Test
    fun missingFileOnTheServerFailsNamingTheFile() {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        servedFiles.remove("en_test.sqlite")

        val error =
            assertThrows(PackInstallException::class.java) {
                runBlocking { repository.install(entry("en_test")) {} }
            }

        assertEquals("en_test.sqlite", error.fileName)
        assertTrue(error.message.orEmpty().contains("404"))
    }

    @Test
    fun fontArchiveIsExtractedToOneFilePerPage() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        assertNull(repository.fontFile(ScriptId.V2, 2))

        repository.install(entry("v2")) {}

        assertEquals("two", repository.fontFile(ScriptId.V2, 2)!!.readText())
        assertEquals("p3.ttf", repository.fontFile(ScriptId.V2, 3)!!.name)
        assertNull(repository.fontFile(ScriptId.V2, 4))
        assertNull(repository.fontFile(ScriptId.V1, 1))
        assertThrows(IllegalArgumentException::class.java) { repository.fontFile(ScriptId.V2, 605) }
        assertEquals("font", repository.installed().first().first { it.id == "v2" }.kind)
    }

    @Test
    fun everyPageTypeInstallsItsOwnFontFilesAndNoOtherTypeSeesThem() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        for (script in listOf(ScriptId.V1, ScriptId.V2, ScriptId.V4Tajweed)) {
            assertNull(script.name, repository.fontFile(script, 2))
        }

        repository.install(entry("v1")) {}
        assertEquals("v1-two", repository.fontFile(ScriptId.V1, 2)!!.readText())
        assertNull(repository.fontFile(ScriptId.V2, 2))
        assertNull(repository.fontFile(ScriptId.V4Tajweed, 2))

        repository.install(entry("v4-tajweed")) {}
        repository.install(entry("v2")) {}

        assertEquals("v1-two", repository.fontFile(ScriptId.V1, 2)!!.readText())
        assertEquals("two", repository.fontFile(ScriptId.V2, 2)!!.readText())
        assertEquals("v4-two", repository.fontFile(ScriptId.V4Tajweed, 2)!!.readText())
        val installedIds = repository.installed().first().map { it.id }
        assertTrue(installedIds.containsAll(listOf("v1", "v2", "v4-tajweed")))
    }

    @Test
    fun sizeOfListsTheDownloadSizeBeforeInstallAndTheDiskSizeAfter() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))

        val before = repository.sizeOf("v2")!!
        assertEquals(fontArchive.size.toLong(), before.downloadBytes)
        assertNull(before.installedBytes)

        repository.install(entry("v2")) {}

        val after = repository.sizeOf("v2")!!
        assertEquals(before.downloadBytes, after.downloadBytes)
        assertEquals(
            repository.installed().first().first { it.id == "v2" }.bytes,
            after.installedBytes,
        )
        assertTrue(after.installedBytes!! > 0)
        assertNull(repository.sizeOf("no_such_pack"))
    }

    @Test
    fun bundledFontIsCopiedOutOfTheApkAndNeverRemoved() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))

        val font = repository.fontFile(ScriptId.QpcHafs, 123)

        assertNotNull(font)
        assertEquals(hafsFont.toList(), font!!.readBytes().toList())
        assertTrue(repository.installed().first().any { it.id == "qpc-hafs" })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.remove("qpc-hafs") }
        }
        assertNull(repository.fontFile(ScriptId.IndoPak, 1))
    }

    @Test
    fun removeDeletesTheFilesAndUpdatesTheInstalledList() = runBlocking {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))
        repository.install(entry("en_test")) {}
        repository.install(entry("v2")) {}

        repository.remove("en_test")
        repository.remove("v2")

        assertFalse(File(storage.root, "en_test.sqlite").exists())
        assertNull(repository.fontFile(ScriptId.V2, 1))
        assertFalse(repository.installed().first().any { it.id == "en_test" || it.id == "v2" })
    }

    @Test
    fun removingAnUnknownPackFailsLoudly() {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile()))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.remove("ghost") }
        }
    }

    @Test
    fun databaseNewerThanTheSupportedFormatIsRejected() {
        buildWorld(mapOf("en_test.sqlite" to translationPackFile(formatVersion = 2)))

        val error =
            assertThrows(PackInstallException::class.java) {
                runBlocking { repository.install(entry("en_test")) {} }
            }

        assertEquals("en_test.sqlite", error.fileName)
        assertTrue(error.message.orEmpty().contains("format_version 2"))
        assertFalse(File(storage.root, "en_test.sqlite").exists())
    }
}
