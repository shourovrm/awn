package app.awn.quran.core.data.models

import app.awn.quran.core.data.packs.PackDownloader
import app.awn.quran.core.data.packs.PackFailureReason
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Downloads from a local HTTP server that implements Range requests. */
class SpeechModelStoreTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var server: HttpServer
    private val servedFiles = mutableMapOf<String, ByteArray>()
    private val rangeHeadersSeen = mutableListOf<String?>()
    private var statusOverride: Int? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tokensBytes = "tokens-bytes".toByteArray()
    private val modelBytes = ByteArray(300_000) { (it % 251).toByte() }
    private val specFiles =
        listOf(fileSpecOf("tokens.txt", tokensBytes), fileSpecOf("model.onnx", modelBytes))

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = servedFiles[exchange.requestURI.path.removePrefix("/")]
            val range = exchange.requestHeaders.getFirst("Range")
            rangeHeadersSeen += range
            val status = statusOverride
            when {
                status != null -> exchange.sendResponseHeaders(status, -1)
                body == null -> exchange.sendResponseHeaders(404, -1)
                range != null -> {
                    val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    val rest = body.copyOfRange(start, body.size)
                    exchange.responseHeaders.add(
                        "Content-Range",
                        "bytes $start-${body.size - 1}/${body.size}",
                    )
                    exchange.sendResponseHeaders(206, rest.size.toLong())
                    exchange.responseBody.use { it.write(rest) }
                }
                else -> {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
            exchange.close()
        }
        server.start()
        servedFiles["tokens.txt"] = tokensBytes
        servedFiles["model.onnx"] = modelBytes
    }

    @After
    fun stopServer() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        server.stop(0)
    }

    private fun fileSpecOf(name: String, content: ByteArray) =
        SpeechModelFile(
            name = name,
            bytes = content.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(content).toHex(),
        )

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun storeIn(
        directory: File,
        usableBytes: Long = Long.MAX_VALUE,
    ): SpeechModelStore =
        SpeechModelStore(
            directory = directory,
            scope = scope,
            files = specFiles,
            baseUrl = "http://127.0.0.1:${server.address.port}",
            downloader = PackDownloader(),
            usableBytesOf = { usableBytes },
        )

    private fun finish(store: SpeechModelStore): SpeechModelState = runBlocking {
        withTimeout(30_000) { store.download().join() }
        store.state.value
    }

    @Test
    fun aFreshPhoneHasNoModel() {
        val store = storeIn(temporaryFolder.newFolder("models"))

        assertEquals(SpeechModelState.NotInstalled, store.state.value)
        assertFalse(store.isInstalled())
        assertNull(store.installedFiles())
    }

    @Test
    fun downloadVerifiesEveryFileAndInstallsThemWithoutLeavingPartialFiles() {
        val directory = temporaryFolder.newFolder("models")
        val store = storeIn(directory)

        val state = finish(store)

        assertEquals(SpeechModelState.Installed, state)
        assertTrue(store.isInstalled())
        assertEquals(tokensBytes.toList(), File(directory, "tokens.txt").readBytes().toList())
        assertEquals(modelBytes.toList(), File(directory, "model.onnx").readBytes().toList())
        assertEquals(
            emptyList<String>(),
            directory.walkTopDown().filter { it.name.endsWith(".part") }.map { it.name }.toList(),
        )
        assertNotNull(store.installedFiles())
    }

    @Test
    fun anInterruptedDownloadResumesFromTheBytesAlreadyOnDisk() {
        val directory = temporaryFolder.newFolder("models")
        val partial = File(File(directory, ".downloads"), "model.onnx.part")
        partial.parentFile!!.mkdirs()
        partial.writeBytes(modelBytes.copyOfRange(0, 100_000))
        val store = storeIn(directory)

        val state = finish(store)

        assertEquals(SpeechModelState.Installed, state)
        assertTrue("a Range request was sent", "bytes=100000-" in rangeHeadersSeen)
        assertEquals(modelBytes.toList(), File(directory, "model.onnx").readBytes().toList())
    }

    @Test
    fun aFileWithTheWrongChecksumIsDiscardedAndReportedAsDamaged() {
        val directory = temporaryFolder.newFolder("models")
        servedFiles["model.onnx"] = ByteArray(modelBytes.size) { 7 }
        val store = storeIn(directory)

        val state = finish(store)

        assertTrue(state is SpeechModelState.Failed)
        assertEquals(PackFailureReason.Damaged, (state as SpeechModelState.Failed).reason)
        assertFalse(File(directory, "model.onnx").exists())
        assertFalse(File(File(directory, ".downloads"), "model.onnx.part").exists())
        assertFalse(store.isInstalled())
    }

    @Test
    fun aServerErrorIsShownAsAFailureAndInstallsNothingFromThatFile() {
        val directory = temporaryFolder.newFolder("models")
        statusOverride = 500
        val store = storeIn(directory)

        val state = finish(store)

        assertEquals(PackFailureReason.ServerError, (state as SpeechModelState.Failed).reason)
        assertFalse(store.isInstalled())
    }

    @Test
    fun aFullPhoneFailsBeforeAnyRequestWithTheNumbers() {
        val directory = temporaryFolder.newFolder("models")
        val store = storeIn(directory, usableBytes = 1_000L)

        val state = finish(store) as SpeechModelState.Failed

        assertEquals(PackFailureReason.NoSpace, state.reason)
        assertEquals(1_000L, state.freeBytes)
        assertEquals((tokensBytes.size + modelBytes.size).toLong(), state.neededBytes)
        assertEquals(emptyList<String?>(), rangeHeadersSeen)
    }

    @Test
    fun theDownloadCanBeTriedAgainAfterAFailure() {
        val directory = temporaryFolder.newFolder("models")
        statusOverride = 500
        val store = storeIn(directory)
        finish(store)

        statusOverride = null
        val state = finish(store)

        assertEquals(SpeechModelState.Installed, state)
    }

    @Test
    fun filesAlreadyOnThePhoneWithTheRightSizeCountAsInstalled() {
        val directory = temporaryFolder.newFolder("models")
        File(directory, "tokens.txt").writeBytes(tokensBytes)
        File(directory, "model.onnx").writeBytes(modelBytes)

        val store = storeIn(directory)

        assertEquals(SpeechModelState.Installed, store.state.value)
    }

    @Test
    fun aFileOfTheWrongSizeIsNotInstalledAndIsFetchedAgain() {
        val directory = temporaryFolder.newFolder("models")
        File(directory, "tokens.txt").writeBytes(tokensBytes)
        File(directory, "model.onnx").writeBytes(modelBytes.copyOf(10))

        val store = storeIn(directory)
        assertEquals(SpeechModelState.NotInstalled, store.state.value)

        assertEquals(SpeechModelState.Installed, finish(store))
        assertEquals(modelBytes.size.toLong(), File(directory, "model.onnx").length())
    }

    @Test
    fun removeDeletesTheFilesAndAnyPartialDownload() = runBlocking {
        val directory = temporaryFolder.newFolder("models")
        val store = storeIn(directory)
        finish(store)
        File(File(directory, ".downloads"), "model.onnx.part").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        store.remove()

        assertEquals(SpeechModelState.NotInstalled, store.state.value)
        assertFalse(File(directory, "model.onnx").exists())
        assertFalse(File(directory, "tokens.txt").exists())
        assertFalse(File(File(directory, ".downloads"), "model.onnx.part").exists())
    }

    @Test
    fun cancelKeepsThePartialFileForTheNextAttempt() = runBlocking {
        val directory = temporaryFolder.newFolder("models")
        val partial = File(File(directory, ".downloads"), "model.onnx.part")
        partial.parentFile!!.mkdirs()
        partial.writeBytes(modelBytes.copyOfRange(0, 1_000))
        val store = storeIn(directory)
        // The server answers slowly enough that the cancel lands mid-download.
        server.removeContext("/")
        server.createContext("/") { exchange ->
            Thread.sleep(2_000)
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }

        val job = store.download()
        store.cancel()
        job.cancelAndJoin()

        assertEquals(SpeechModelState.NotInstalled, store.state.value)
        assertTrue(partial.exists())
    }

    @Test
    fun theSpecMatchesTheModelPinsThatTheBuildChecks() {
        val pins =
            File("../../gradle/models.sha256")
                .readLines()
                .filter { it.isNotBlank() }
                .associate { line ->
                    val (hash, name) = line.trim().split(Regex("\\s+"), limit = 2)
                    name.trimStart('*') to hash
                }

        for (file in SpeechModelSpec.files) {
            assertEquals("pin of ${file.name}", pins[file.name], file.sha256)
        }
        assertEquals(3, SpeechModelSpec.files.size)
    }
}
