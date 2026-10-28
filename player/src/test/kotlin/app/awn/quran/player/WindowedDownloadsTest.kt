package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fakes.FakeReciterRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SURAH_FILE_BYTES = 130_000

/** Serves surah files of [SURAH_FILE_BYTES] bytes and answers `Range: bytes=a-b` with 206. */
private class FakeSurahFileServer : Interceptor {

    val requests = mutableListOf<Pair<String, String?>>()
    var ignoreRange = false

    fun contentOf(url: String): ByteArray {
        val seed = url.substringAfterLast('/').filter { it.isDigit() }.toInt()
        return ByteArray(SURAH_FILE_BYTES) { ((it * 7) + seed).toByte() }
    }

    @Suppress("ReturnCount") // Each branch is one canned server answer.
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val rangeHeader = request.header("Range")
        requests += url to rangeHeader
        val content = contentOf(url)
        if (request.method == "HEAD") return headAnswer(request)
        if (rangeHeader == null || ignoreRange) return answer(request, 200, content)

        val bounds = RANGE.matchEntire(rangeHeader) ?: error("Unexpected Range: $rangeHeader")
        val first = bounds.groupValues[1].toInt()
        val last = bounds.groupValues[2].toIntOrNull() ?: (content.size - 1)
        val slice = content.copyOfRange(first, minOf(last, content.size - 1) + 1)
        return answer(request, 206, slice, "bytes $first-${first + slice.size - 1}/${content.size}")
    }

    private fun headAnswer(request: Request): Response {
        val body =
            object : ResponseBody() {
                override fun contentType() = "audio/mpeg".toMediaType()

                override fun contentLength() = SURAH_FILE_BYTES.toLong()

                override fun source(): BufferedSource = Buffer()
            }
        return responseBuilder(request, 200).body(body).build()
    }

    private fun answer(
        request: Request,
        code: Int,
        body: ByteArray,
        contentRange: String? = null,
    ): Response {
        val builder =
            responseBuilder(request, code).body(body.toResponseBody("audio/mpeg".toMediaType()))
        if (contentRange != null) builder.header("Content-Range", contentRange)
        return builder.build()
    }

    private fun responseBuilder(request: Request, code: Int) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")

    private companion object {
        val RANGE = Regex("bytes=(\\d+)-(\\d*)")
    }
}

/**
 * Downloads of a reciter with one mp3 per surah: each ayah is fetched as a byte range of the surah
 * file and stored under the name a per-ayah file has. In the fixture ayah n of a surah holds bytes
 * `n * 32000` to `(n + 1) * 32000`, and the last ayah of a surah holds the bytes from its start to
 * the end of the file.
 */
class WindowedDownloadsTest {

    @get:Rule val folder = TemporaryFolder()

    private val server = FakeSurahFileServer()
    private val client = OkHttpClient.Builder().addInterceptor(server).build()
    private val surahOneUrl = WINDOWED_SURAH_ONE_URL
    private val surahTwoUrl = "https://download.quranicaudio.com/quran/windowed/002.mp3"

    private fun downloads(
        reciters: FakeReciterRepository = fakeWindowedReciters(),
    ): OkHttpAudioDownloads =
        OkHttpAudioDownloads(
            audioRoot = folder.root,
            reciters = reciters,
            quranText = fakeQuranText(),
            client = client,
        )

    private fun clipFile(name: String) = File(folder.root, "windowed/$name")

    private fun surahBytes(url: String, first: Int, lastIncluded: Int?): ByteArray =
        server.contentOf(url).copyOfRange(first, (lastIncluded ?: (SURAH_FILE_BYTES - 1)) + 1)

    @Test
    fun ayahIsFetchedAsItsByteRangeAndStoredUnderThePerAyahName() = runBlocking {
        downloads().download("windowed", listOf(AyahRef(1, 2)))

        assertEquals(listOf(surahOneUrl to "bytes=64000-96000"), server.requests)
        val expected = surahBytes(surahOneUrl, 64_000, 96_000)
        assertArrayEquals(expected, clipFile("001002.mp3").readBytes())
        assertFalse(clipFile("001.mp3").exists())
    }

    @Test
    fun lastAyahOfASurahAsksForAnOpenEndedRange() = runBlocking {
        downloads().download("windowed", listOf(AyahRef(1, 3)))

        assertEquals(listOf(surahOneUrl to "bytes=96000-"), server.requests)
        assertArrayEquals(surahBytes(surahOneUrl, 96_000, null), clipFile("001003.mp3").readBytes())
    }

    @Test
    fun anAnswerOtherThan206FailsWithContext() {
        server.ignoreRange = true

        val failure =
            assertThrows(IOException::class.java) {
                runBlocking { downloads().download("windowed", listOf(AyahRef(1, 2))) }
            }

        assertTrue(failure.message, failure.message!!.contains("Expected 206"))
        assertTrue(failure.message, failure.message!!.contains("bytes=64000-96000"))
        assertFalse(clipFile("001002.mp3").exists())
    }

    @Test
    fun resumeAsksForTheBytesAfterTheOnesAlreadyStored() = runBlocking {
        val whole = surahBytes(surahOneUrl, 64_000, 96_000)
        clipFile("001002.mp3.part").apply {
            parentFile?.mkdirs()
            writeBytes(whole.copyOfRange(0, 1_000))
        }

        downloads().download("windowed", listOf(AyahRef(1, 2)))

        assertEquals(listOf(surahOneUrl to "bytes=65000-96000"), server.requests)
        assertArrayEquals(whole, clipFile("001002.mp3").readBytes())
    }

    @Test
    fun aPartFileThatAlreadyHoldsTheWholeClipIsKeptWithoutARequest() = runBlocking {
        val whole = surahBytes(surahOneUrl, 64_000, 96_000)
        clipFile("001002.mp3.part").apply {
            parentFile?.mkdirs()
            writeBytes(whole)
        }

        downloads().download("windowed", listOf(AyahRef(1, 2)))

        assertTrue(server.requests.isEmpty())
        assertArrayEquals(whole, clipFile("001002.mp3").readBytes())
    }

    @Test
    fun pageDownloadFetchesOnlyTheAyahsOfThePage() = runBlocking {
        downloads().downloadPage("windowed", listOf(AyahRef(2, 1), AyahRef(2, 2)))

        assertEquals(
            listOf(surahTwoUrl to "bytes=32000-64000", surahTwoUrl to "bytes=64000-96000"),
            server.requests,
        )
        assertTrue(clipFile("002001.mp3").isFile)
        assertTrue(clipFile("002002.mp3").isFile)
        assertFalse(clipFile("002003.mp3").exists())
        assertFalse(clipFile("001001.mp3").exists())
    }

    @Test
    fun statusAndRemovalWorkPerAyah() = runBlocking {
        val downloads = downloads()
        downloads.download("windowed", listOf(AyahRef(1, 2)))

        val status = downloads.status("windowed", listOf(AyahRef(1, 2), AyahRef(1, 3)))
        assertEquals(1, status.presentAyahs)
        assertEquals(2, status.totalAyahs)
        assertEquals(32_001L, status.presentBytes)
        assertTrue(downloads.hasAyahFiles("windowed"))

        downloads.removeAyahs("windowed", listOf(AyahRef(1, 2)))
        assertFalse(clipFile("001002.mp3").exists())
    }

    @Test
    fun estimateUsesTheByteRangesAndTheFileSizeForTheLastAyah() = runBlocking {
        val downloads = downloads()

        val twoClosedClips = listOf(AyahRef(1, 1), AyahRef(1, 2))
        assertEquals(64_000L, downloads.estimateBytes("windowed", twoClosedClips))
        // The last ayah starts at byte 96000 of a 130000 byte file.
        assertEquals(34_000L, downloads.estimateBytes("windowed", listOf(AyahRef(1, 3))))
    }

    @Test
    fun withoutByteDataTheWholeSurahFileIsDownloaded() = runBlocking {
        downloads(fakeWindowedReciters(withByteData = false))
            .downloadPage("windowed", listOf(AyahRef(1, 2), AyahRef(1, 3)))

        assertEquals(listOf(surahOneUrl to null), server.requests)
        assertArrayEquals(surahBytes(surahOneUrl, 0, null), clipFile("001.mp3").readBytes())
    }
}
