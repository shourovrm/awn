package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Plays the part of the audio server: every file is [FILE_BYTES] long and honours `Range`. */
private class FakeAudioServer : Interceptor {

    val requests = mutableListOf<Pair<String, String?>>()
    var failComHost = false
    var ignoreRange = false
    var truncateBodyTo: Int? = null

    fun contentOf(url: String): ByteArray {
        val seed = url.substringAfterLast('/').filter { it.isDigit() }.toInt()
        return ByteArray(FILE_BYTES) { ((it * 31) + seed).toByte() }
    }

    @Suppress("ReturnCount") // Each branch is one canned server answer.
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val range = request.header("Range")
        requests += url to range
        if (failComHost && request.url.host.endsWith(".quranicaudio.com")) {
            return reply(request, 503, ByteArray(0))
        }
        val content = contentOf(url)
        val start =
            if (range != null && !ignoreRange)
                range.removePrefix("bytes=").removeSuffix("-").toInt()
            else 0
        if (start >= content.size) {
            return reply(request, 416, ByteArray(0), "bytes */${content.size}")
        }
        val slice = content.copyOfRange(start, content.size)
        return if (start > 0) {
            reply(request, 206, slice, "bytes $start-${content.size - 1}/${content.size}")
        } else {
            reply(request, 200, slice)
        }
    }

    private fun reply(
        request: okhttp3.Request,
        code: Int,
        body: ByteArray,
        contentRange: String? = null,
    ): Response {
        val truncateTo = truncateBodyTo
        val responseBody: ResponseBody =
            if (truncateTo != null && code < 300) {
                val declaredLength = body.size.toLong()
                val shortened = Buffer().write(body, 0, truncateTo)
                object : ResponseBody() {
                    override fun contentType() = "audio/mpeg".toMediaType()

                    override fun contentLength() = declaredLength

                    override fun source() = shortened
                }
            } else {
                body.toResponseBody("audio/mpeg".toMediaType())
            }
        val builder =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(responseBody)
        if (contentRange != null) builder.header("Content-Range", contentRange)
        return builder.build()
    }

    companion object {
        const val FILE_BYTES = 1_000
    }
}

class OkHttpAudioDownloadsTest {

    @get:Rule val folder = TemporaryFolder()

    private val server = FakeAudioServer()
    private val client = OkHttpClient.Builder().addInterceptor(server).build()
    private val timedFirstAyahUrl =
        "https://mirrors.quranicaudio.com/everyayah/Timed_64kbps/001001.mp3"

    private fun downloads(
        policy: DownloadNetworkPolicy = DownloadNetworkPolicy.Always,
    ): OkHttpAudioDownloads =
        OkHttpAudioDownloads(
            audioRoot = folder.root,
            reciters = fakeReciters(),
            quranText = fakeQuranText(),
            client = client,
            networkPolicy = policy,
        )

    private fun audioFile(name: String) = File(folder.root, "alafasy/$name")

    @Test
    fun surahDownloadFetchesEveryAyahFromTheUrlsPlaybackUses() = runBlocking {
        downloads().downloadSurah("alafasy", 1)

        assertEquals(
            listOf(
                timedFirstAyahUrl,
                "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001002.mp3",
                "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001003.mp3",
            ),
            server.requests.map { it.first },
        )
        for (name in listOf("001001.mp3", "001002.mp3", "001003.mp3")) {
            assertEquals(FakeAudioServer.FILE_BYTES.toLong(), audioFile(name).length())
        }
        assertEquals(3_000L, downloads().bytesUsed("alafasy"))
    }

    @Test
    fun surahOnlyReciterDownloadsOneSurahFile() = runBlocking {
        downloads().downloadSurah("raad", 2)
        assertEquals(
            listOf("https://download.quranicaudio.com/quran/raad/mp3/002.mp3"),
            server.requests.map { it.first },
        )
        assertTrue(File(folder.root, "raad/002.mp3").isFile)
    }

    @Test
    fun pageDownloadFetchesOnlyThoseAyahs() = runBlocking {
        downloads().downloadPage("alafasy", listOf(AyahRef(1, 2), AyahRef(2, 1)))
        assertTrue(audioFile("001002.mp3").isFile)
        assertTrue(audioFile("002001.mp3").isFile)
        assertFalse(audioFile("001001.mp3").exists())
    }

    @Test
    fun failedPrimaryHostFallsBackToOrgDomain() = runBlocking {
        server.failComHost = true
        downloads().downloadPage("alafasy", listOf(AyahRef(1, 2)))

        assertEquals(
            listOf(
                "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001002.mp3",
                "https://mirrors.quranicaudio.org/everyayah/Alafasy_128kbps/001002.mp3",
            ),
            server.requests.map { it.first },
        )
        assertTrue(audioFile("001002.mp3").isFile)
    }

    @Test
    fun partialFileIsContinuedWithARangeRequest() = runBlocking {
        val url = "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001002.mp3"
        val expected = server.contentOf(url)
        audioFile("001002.mp3.part").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(expected.copyOfRange(0, 400))
        }

        downloads().downloadPage("alafasy", listOf(AyahRef(1, 2)))

        assertEquals(listOf(url to "bytes=400-"), server.requests)
        assertArrayEquals(expected, audioFile("001002.mp3").readBytes())
        assertFalse(audioFile("001002.mp3.part").exists())
    }

    @Test
    fun serverThatIgnoresRangeRestartsTheFile() = runBlocking {
        server.ignoreRange = true
        val url = "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001002.mp3"
        audioFile("001002.mp3.part").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(ByteArray(400) { 9 })
        }

        downloads().downloadPage("alafasy", listOf(AyahRef(1, 2)))

        assertArrayEquals(server.contentOf(url), audioFile("001002.mp3").readBytes())
    }

    @Test
    fun alreadyCompletePartIsPromotedWithoutRewriting() = runBlocking {
        val url = "https://mirrors.quranicaudio.com/everyayah/Alafasy_128kbps/001002.mp3"
        audioFile("001002.mp3.part").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(server.contentOf(url))
        }

        downloads().downloadPage("alafasy", listOf(AyahRef(1, 2)))

        assertArrayEquals(server.contentOf(url), audioFile("001002.mp3").readBytes())
    }

    @Test
    fun shortBodyFailsTheSizeCheckAndKeepsThePartForResume() {
        server.truncateBodyTo = 300
        assertThrows(IOException::class.java) {
            runBlocking { downloads().downloadPage("alafasy", listOf(AyahRef(1, 2))) }
        }
        assertFalse(audioFile("001002.mp3").exists())
        // The fallback domain resumed the partial file and was cut short again.
        assertEquals(600L, audioFile("001002.mp3.part").length())
    }

    @Test
    fun progressFlowReportsGrowingBytesThenComplete() = runBlocking {
        val downloads = downloads()
        assertEquals(
            DownloadState("alafasy", 1, 0L, null, false),
            downloads.state("alafasy", 1).first(),
        )

        val seen = mutableListOf<DownloadState>()
        val collector =
            launch(Dispatchers.Unconfined) { downloads.state("alafasy", 1).collect { seen += it } }
        downloads.downloadSurah("alafasy", 1)
        collector.cancel()

        val bytes = seen.map { it.downloadedBytes }
        assertEquals(bytes.sorted(), bytes)
        val last = seen.last()
        assertTrue(last.complete)
        assertEquals(3_000L, last.downloadedBytes)
        assertEquals(3_000L, last.totalBytes)
        assertTrue(seen.any { !it.complete && it.downloadedBytes in 1..2_999 })
    }

    @Test
    fun stateOfAnUntouchedSurahIsReadFromDisk() = runBlocking {
        downloads().downloadSurah("alafasy", 1)
        val state = downloads().state("alafasy", 1).first()
        assertTrue(state.complete)
        assertEquals(3_000L, state.downloadedBytes)
    }

    @Test
    fun removeDeletesFilesAndResetsState() = runBlocking {
        val downloads = downloads()
        downloads.downloadSurah("alafasy", 1)
        downloads.remove("alafasy")

        assertEquals(0L, downloads.bytesUsed("alafasy"))
        assertEquals(
            DownloadState("alafasy", 1, 0L, null, false),
            downloads.state("alafasy", 1).first(),
        )
    }

    @Test
    fun policyRefusalThrowsBeforeAnyRequest() {
        assertThrows(DownloadNotAllowedException::class.java) {
            runBlocking {
                downloads(DownloadNetworkPolicy { false }).downloadSurah("alafasy", 1)
            }
        }
        assertTrue(server.requests.isEmpty())
    }
}
