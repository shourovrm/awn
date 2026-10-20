package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ReciterRepository
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val COPY_BUFFER_BYTES = 16 * 1024

/**
 * Downloads reciter audio into `<audioRoot>/<reciterId>/` (see [LocalAudioStore]) with OkHttp.
 *
 * - A file is written as `<name>.part` and renamed once its size matches the server's
 *   `Content-Length`, so a file with its final name is whole.
 * - A `.part` file left by an interrupted download is continued with an HTTP `Range` request.
 * - A failed request is tried again on the `.quranicaudio.org` domain.
 * - One download runs at a time; later calls wait for the earlier ones.
 *
 * While a surah downloads, [DownloadState.totalBytes] is an estimate (bytes so far scaled by the
 * share of files present) because the server is not asked for every file's size up front. It is
 * exact once [DownloadState.complete] is true.
 */
@Suppress("TooManyFunctions") // Disk, state and HTTP helpers of one downloader.
class OkHttpAudioDownloads(
    audioRoot: File,
    reciters: ReciterRepository,
    private val quranText: QuranTextRepository,
    private val client: OkHttpClient = OkHttpClient(),
    private val networkPolicy: DownloadNetworkPolicy = DownloadNetworkPolicy.Always,
) : AudioDownloads {

    private data class SurahKey(val reciterId: String, val surah: Int)

    private class Target(val url: String, val file: File)

    /** What is on disk for one surah of one reciter, plus bytes written during this run. */
    private class Progress(val totalFiles: Int, var filesPresent: Int, var bytes: Long) {
        val complete: Boolean
            get() = totalFiles > 0 && filesPresent >= totalFiles
    }

    private class ContentRange(val start: Long?, val total: Long?)

    private val store = LocalAudioStore(audioRoot)
    private val resolver = ReciterAudioResolver(reciters, store)
    private val downloadLock = Mutex()
    private val states = MutableStateFlow<Map<SurahKey, DownloadState>>(emptyMap())

    override fun state(reciterId: String, surah: Int): Flow<DownloadState> = flow {
        val key = SurahKey(reciterId, surah)
        if (key !in states.value) {
            val onDisk = toState(key, scanDisk(key))
            states.update { if (key in it) it else it + (key to onDisk) }
        }
        emitAll(states.map { it[key] }.filterNotNull().distinctUntilChanged())
    }

    override suspend fun downloadPage(reciterId: String, ayahs: List<AyahRef>) {
        val ayahsBySurah = ayahs.groupBy { it.surah }
        val targetsBySurah = ayahsBySurah.mapValues { (_, surahAyahs) ->
            targetsFor(reciterId, surahAyahs)
        }
        download(reciterId, targetsBySurah)
    }

    override suspend fun downloadSurah(reciterId: String, surah: Int) {
        val allAyahs = (1..verseCount(surah)).map { AyahRef(surah, it) }
        download(reciterId, mapOf(surah to targetsFor(reciterId, allAyahs)))
    }

    override suspend fun bytesUsed(reciterId: String): Long =
        withContext(Dispatchers.IO) {
            store
                .reciterDirectory(reciterId)
                .walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }

    override suspend fun remove(reciterId: String) {
        downloadLock.withLock {
            withContext(Dispatchers.IO) { store.reciterDirectory(reciterId).deleteRecursively() }
            states.update { current ->
                current.mapValues { (key, state) ->
                    if (key.reciterId == reciterId) emptyState(key) else state
                }
            }
        }
    }

    private suspend fun targetsFor(reciterId: String, ayahs: List<AyahRef>): List<Target> =
        ayahs
            .map { ayah ->
                val audio =
                    resolver.resolve(reciterId, ayah)
                        ?: throw FileNotFoundException("No audio for $ayah from $reciterId")
                val file =
                    checkNotNull(store.fileForKey(audio.source.localKey)) {
                        "Bad file key ${audio.source.localKey}"
                    }
                Target(audio.source.url, file)
            }
            .distinctBy { it.file.path }

    private suspend fun download(reciterId: String, targetsBySurah: Map<Int, List<Target>>) {
        if (!networkPolicy.mayDownload()) throw DownloadNotAllowedException()
        downloadLock.withLock {
            for ((surah, targets) in targetsBySurah) {
                downloadSurahTargets(SurahKey(reciterId, surah), targets)
            }
        }
    }

    private suspend fun downloadSurahTargets(key: SurahKey, targets: List<Target>) {
        val progress = scanDisk(key)
        publish(key, progress)
        try {
            for (target in targets) {
                if (target.file.isFile) continue
                withContext(Dispatchers.IO) {
                    fetchWithFallback(target) { writtenBytes ->
                        progress.bytes += writtenBytes
                        publish(key, progress)
                    }
                }
                progress.filesPresent++
                publish(key, progress)
            }
        } finally {
            // Whatever happened, show what is on disk so the progress never shows stale numbers.
            publish(key, scanDisk(key))
        }
    }

    // region Disk

    private suspend fun scanDisk(key: SurahKey): Progress =
        withContext(Dispatchers.IO) {
            val prefix = AudioUrls.padded(key.surah)
            val files =
                store
                    .reciterDirectory(key.reciterId)
                    .listFiles { file ->
                        file.name.startsWith(prefix)
                    }
                    .orEmpty()
            val ayahFileCount = files.count { AYAH_FILE.matches(it.name) }
            val hasSurahFile = files.any { it.name == "$prefix.mp3" }
            val verses = verseCount(key.surah)
            val progress =
                if (hasSurahFile) {
                    Progress(totalFiles = 1, filesPresent = 1, bytes = 0L)
                } else {
                    Progress(totalFiles = verses, filesPresent = ayahFileCount, bytes = 0L)
                }
            progress.bytes = files.sumOf { it.length() }
            progress
        }

    private suspend fun verseCount(surah: Int): Int =
        requireNotNull(quranText.chapters().firstOrNull { it.number == surah }) {
                "No such surah: $surah"
            }
            .versesCount

    // endregion

    // region State

    private fun publish(key: SurahKey, progress: Progress) {
        states.update { it + (key to toState(key, progress)) }
    }

    private fun toState(key: SurahKey, progress: Progress): DownloadState {
        val estimatedTotal =
            when {
                progress.complete -> progress.bytes
                progress.filesPresent > 0 ->
                    maxOf(
                        progress.bytes,
                        progress.bytes * progress.totalFiles / progress.filesPresent,
                    )
                else -> null
            }
        return DownloadState(
            reciterId = key.reciterId,
            surah = key.surah,
            downloadedBytes = progress.bytes,
            totalBytes = estimatedTotal,
            complete = progress.complete,
        )
    }

    private fun emptyState(key: SurahKey) = DownloadState(key.reciterId, key.surah, 0L, null, false)

    // endregion

    // region HTTP

    private suspend fun fetchWithFallback(target: Target, onWrittenBytes: (Long) -> Unit) {
        try {
            fetch(target.url, target.file, onWrittenBytes)
        } catch (primaryFailure: IOException) {
            val fallbackUrl = AudioUrls.fallbackUrl(target.url) ?: throw primaryFailure
            try {
                fetch(fallbackUrl, target.file, onWrittenBytes)
            } catch (fallbackFailure: IOException) {
                fallbackFailure.addSuppressed(primaryFailure)
                throw fallbackFailure
            }
        }
    }

    /** What to do with a response to a download request. */
    private sealed interface Plan {
        /** The `.part` file already holds the whole file. */
        data object AlreadyComplete : Plan

        /** Delete the `.part` file and ask again from byte 0. */
        class Restart(val reason: String) : Plan

        class Copy(val append: Boolean, val expectedTotal: Long?) : Plan
    }

    /**
     * Downloads [url] into [target]. A matching `.part` file is continued; a server that ignores
     * the range request, or answers for a different start, makes it start over once.
     */
    private suspend fun fetch(
        url: String,
        target: File,
        onWrittenBytes: (Long) -> Unit,
        mayRestart: Boolean = true,
    ) {
        target.parentFile?.mkdirs()
        val partFile = File(target.path + ".part")
        val resumeFrom = if (partFile.isFile) partFile.length() else 0L

        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (resumeFrom > 0L) request.header("Range", "bytes=$resumeFrom-")

        val restartReason =
            client.newCall(request.build()).execute().use { response ->
                when (val plan = planFor(response, url, resumeFrom)) {
                    Plan.AlreadyComplete -> {
                        promote(partFile, target)
                        null
                    }
                    is Plan.Restart -> plan.reason
                    is Plan.Copy -> {
                        copyBody(response, partFile, plan.append, onWrittenBytes)
                        checkComplete(url, partFile, plan.expectedTotal)
                        promote(partFile, target)
                        null
                    }
                }
            }
        if (restartReason == null) return
        partFile.delete()
        if (!mayRestart) throw IOException("Cannot resume $url: $restartReason")
        fetch(url, target, onWrittenBytes, mayRestart = false)
    }

    @Suppress("ReturnCount") // One early answer per kind of response.
    private fun planFor(response: Response, url: String, resumeFrom: Long): Plan {
        val range = parseContentRange(response.header("Content-Range"))
        if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
            val partIsWhole = resumeFrom > 0L && range.total == resumeFrom
            return if (partIsWhole) Plan.AlreadyComplete else Plan.Restart("range rejected")
        }
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")

        val contentLength = response.body.contentLength().takeIf { it >= 0L }
        if (response.code != HTTP_PARTIAL_CONTENT) return Plan.Copy(false, contentLength)
        if (range.start != resumeFrom) return Plan.Restart("range starts at ${range.start}")
        return Plan.Copy(true, range.total ?: contentLength?.plus(resumeFrom))
    }

    private suspend fun copyBody(
        response: Response,
        partFile: File,
        append: Boolean,
        onWrittenBytes: (Long) -> Unit,
    ) {
        FileOutputStream(partFile, append).use { output ->
            response.body.byteStream().use { input -> copyStream(input, output, onWrittenBytes) }
        }
    }

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        onWrittenBytes: (Long) -> Unit,
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            currentCoroutineContext().ensureActive()
            val readBytes = input.read(buffer)
            if (readBytes < 0) return
            output.write(buffer, 0, readBytes)
            onWrittenBytes(readBytes.toLong())
        }
    }

    /** A short file stays as `.part` so the next try resumes it; a long one is discarded. */
    private fun checkComplete(url: String, partFile: File, expectedTotal: Long?) {
        if (expectedTotal == null || partFile.length() == expectedTotal) return
        val actual = partFile.length()
        if (actual > expectedTotal) partFile.delete()
        throw IOException("Size mismatch for $url: got $actual bytes, expected $expectedTotal")
    }

    private fun promote(partFile: File, target: File) {
        Files.move(partFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun parseContentRange(header: String?): ContentRange {
        val match =
            header?.let { CONTENT_RANGE.matchEntire(it.trim()) }
                ?: return ContentRange(start = null, total = null)
        return ContentRange(
            start = match.groupValues[1].toLongOrNull(),
            total = match.groupValues[2].toLongOrNull(),
        )
    }

    // endregion

    private companion object {
        val AYAH_FILE = Regex("\\d{6}\\.mp3")
        val CONTENT_RANGE = Regex("bytes (?:(\\d+)-\\d+|\\*)/(\\d+|\\*)")
    }
}
