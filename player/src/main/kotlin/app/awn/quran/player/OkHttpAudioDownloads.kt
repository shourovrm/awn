package app.awn.quran.player

import app.awn.quran.core.data.AyahAudio
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
    private val reciters: ReciterRepository,
    private val quranText: QuranTextRepository,
    private val client: OkHttpClient = OkHttpClient(),
    private val networkPolicy: DownloadNetworkPolicy = DownloadNetworkPolicy.Always,
) : AudioDownloads {

    private data class SurahKey(val reciterId: String, val surah: Int)

    /** [byteRange] is set when only that part of the file at [url] is stored, as [file]. */
    private class Target(val url: String, val file: File, val byteRange: ByteRange? = null)

    /** What is on disk for one surah of one reciter, plus bytes written during this run. */
    private class Progress(val totalFiles: Int, var filesPresent: Int, var bytes: Long) {
        val complete: Boolean
            get() = totalFiles > 0 && filesPresent >= totalFiles
    }

    private class ContentRange(val start: Long?, val total: Long?)

    /** Receives the byte counts of the file being fetched. */
    private interface FileProgress {
        /** The file will be [expectedTotal] bytes (null if unknown); [resumedBytes] are on disk. */
        fun started(expectedTotal: Long?, resumedBytes: Long)

        fun written(bytes: Long)
    }

    /**
     * Counts the bytes of one [download] call over all its files. The total is the sum of the sizes
     * seen so far plus the average of those for the files not yet started, so it settles as the
     * files go by.
     */
    private class BatchProgress(
        targets: List<Target>,
        private val report: (DownloadProgress) -> Unit,
    ) {
        private val totalFiles = targets.size
        private var knownFiles = 0
        private var knownBytes = 0L
        private var doneBytes = 0L
        private var currentExpected: Long? = null
        private var currentDone = 0L

        init {
            for (target in targets.filter { it.file.isFile }) {
                knownFiles++
                knownBytes += target.file.length()
                doneBytes += target.file.length()
            }
        }

        fun fileStarted(expectedTotal: Long?, resumedBytes: Long) {
            // A retry on another domain starts the same file again; forget the first attempt.
            currentExpected?.let {
                knownFiles--
                knownBytes -= it
            }
            doneBytes -= currentDone
            currentExpected = expectedTotal
            currentDone = resumedBytes
            if (expectedTotal != null) {
                knownFiles++
                knownBytes += expectedTotal
            }
            doneBytes += resumedBytes
            publish()
        }

        fun fileWritten(bytes: Long) {
            currentDone += bytes
            doneBytes += bytes
            publish()
        }

        fun fileFinished() {
            currentExpected = null
            currentDone = 0L
        }

        fun publish() {
            val total =
                if (knownFiles == 0) null
                else knownBytes + knownBytes / knownFiles * (totalFiles - knownFiles)
            report(DownloadProgress(doneBytes, total?.let { maxOf(it, doneBytes) }))
        }
    }

    private val store = LocalAudioStore(audioRoot)
    private val resolver = ReciterAudioResolver(reciters, store)
    private val downloadLock = Mutex()

    /** Goes up whenever a file finishes or is deleted, which re-reads [usedBytes]. */
    private val diskRevision = MutableStateFlow(0)
    private val bytesPerMillisecond = mutableMapOf<String, Double>()
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

    override suspend fun download(
        reciterId: String,
        ayahs: List<AyahRef>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val targetsBySurah =
            ayahs
                .distinct()
                .groupBy { it.surah }
                .toSortedMap()
                .mapValues { (_, surahAyahs) ->
                    targetsFor(reciterId, surahAyahs)
                }
        download(reciterId, targetsBySurah, onProgress)
    }

    override suspend fun downloadSurah(reciterId: String, surah: Int) {
        val allAyahs = (1..verseCount(surah)).map { AyahRef(surah, it) }
        download(reciterId, mapOf(surah to targetsFor(reciterId, allAyahs)))
    }

    override fun usedBytes(reciterId: String): Flow<Long> = flow {
        emitAll(diskRevision.map { bytesUsed(reciterId) })
    }

    override suspend fun status(reciterId: String, ayahs: List<AyahRef>): AudioStatus =
        withContext(Dispatchers.IO) {
            val fileLengths =
                store
                    .reciterDirectory(reciterId)
                    .listFiles { file -> file.isFile }
                    .orEmpty()
                    .associate { it.name to it.length() }
            val ayahFiles = usesAyahFiles(reciterId)
            val names = ayahs.distinct().map { fileNameFor(ayahFiles, it) }
            val presentNames = names.filter { it in fileLengths }
            val bytes = presentNames.distinct().sumOf { fileLengths.getValue(it) }
            AudioStatus(
                presentBytes = bytes,
                presentAyahs = presentNames.size,
                totalAyahs = names.size,
            )
        }

    override suspend fun removeAyahs(reciterId: String, ayahs: List<AyahRef>) {
        downloadLock.withLock {
            withContext(Dispatchers.IO) {
                val directory = store.reciterDirectory(reciterId)
                val ayahFiles = usesAyahFiles(reciterId)
                for (name in ayahs.map { fileNameFor(ayahFiles, it) }.distinct()) {
                    File(directory, name).delete()
                    File(directory, "$name.part").delete()
                }
            }
            refreshStates(reciterId)
            diskRevision.update { it + 1 }
        }
    }

    override suspend fun estimateBytes(reciterId: String, ayahs: List<AyahRef>): Long? =
        withContext(Dispatchers.IO) {
            val distinctAyahs = ayahs.distinct()
            if (distinctAyahs.isEmpty()) return@withContext 0L
            try {
                when {
                    usesClips(reciterId) -> estimateClips(reciterId, distinctAyahs)
                    usesAyahFiles(reciterId) -> estimateAyahFiles(reciterId, distinctAyahs)
                    else -> estimateSurahFiles(reciterId, distinctAyahs)
                }
            } catch (@Suppress("SwallowedException") offline: IOException) {
                null
            }
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
            diskRevision.update { it + 1 }
        }
    }

    /** Re-reads the on-disk state of every surah that has a published state for [reciterId]. */
    private suspend fun refreshStates(reciterId: String) {
        val keys = states.value.keys.filter { it.reciterId == reciterId }
        for (key in keys) publish(key, scanDisk(key))
    }

    private suspend fun targetsFor(reciterId: String, ayahs: List<AyahRef>): List<Target> =
        ayahs
            .map { ayah ->
                val timed = reciters.ayahAudio(reciterId, ayah)
                if (timed != null && timed.canBeFetchedAsClip) {
                    return@map clipTarget(reciterId, ayah, timed)
                }
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

    /** The ayah's own bytes of its surah file, stored under the name a per-ayah file has. */
    private fun clipTarget(reciterId: String, ayah: AyahRef, timed: AyahAudio): Target {
        val key = store.ayahKey(reciterId, ayah)
        val file = checkNotNull(store.fileForKey(key)) { "Bad file key $key" }
        return Target(timed.url, file, timed.clipByteRange())
    }

    private suspend fun download(
        reciterId: String,
        targetsBySurah: Map<Int, List<Target>>,
        onProgress: (DownloadProgress) -> Unit = {},
    ) {
        if (!networkPolicy.mayDownload()) throw DownloadNotAllowedException()
        downloadLock.withLock {
            val batch = BatchProgress(targetsBySurah.values.flatten(), onProgress)
            batch.publish()
            for ((surah, targets) in targetsBySurah) {
                downloadSurahTargets(SurahKey(reciterId, surah), targets, batch)
            }
        }
    }

    private suspend fun downloadSurahTargets(
        key: SurahKey,
        targets: List<Target>,
        batch: BatchProgress,
    ) {
        val progress = scanDisk(key)
        publish(key, progress)
        val fileProgress =
            object : FileProgress {
                override fun started(expectedTotal: Long?, resumedBytes: Long) =
                    batch.fileStarted(expectedTotal, resumedBytes)

                override fun written(bytes: Long) {
                    progress.bytes += bytes
                    batch.fileWritten(bytes)
                    publish(key, progress)
                }
            }
        try {
            for (target in targets) {
                if (target.file.isFile) continue
                withContext(Dispatchers.IO) { fetchWithFallback(target, fileProgress) }
                batch.fileFinished()
                progress.filesPresent++
                publish(key, progress)
                diskRevision.update { it + 1 }
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

    private suspend fun fetchWithFallback(target: Target, fileProgress: FileProgress) {
        try {
            fetch(target.url, target.file, fileProgress, target.byteRange)
        } catch (primaryFailure: IOException) {
            val fallbackUrl = AudioUrls.fallbackUrl(target.url) ?: throw primaryFailure
            try {
                fetch(fallbackUrl, target.file, fileProgress, target.byteRange)
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
     * Downloads [url] into [target], or only [byteRange] of it when that is set. A matching `.part`
     * file is continued; a server that ignores the range request, or answers for a different start,
     * makes it start over once.
     */
    private suspend fun fetch(
        url: String,
        target: File,
        fileProgress: FileProgress,
        byteRange: ByteRange? = null,
        mayRestart: Boolean = true,
    ) {
        target.parentFile?.mkdirs()
        val partFile = File(target.path + ".part")
        val resumeFrom = if (partFile.isFile) partFile.length() else 0L
        if (byteRange?.length == resumeFrom) {
            // An earlier try stored the whole clip and stopped before the rename; asking for the
            // rest would be a range that starts after its end.
            fileProgress.started(resumeFrom, resumeFrom)
            promote(partFile, target)
            return
        }

        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
        rangeHeaderFor(byteRange, resumeFrom)?.let { request.header("Range", it) }

        val restartReason =
            client.newCall(request.build()).execute().use { response ->
                when (val plan = planFor(response, url, resumeFrom, byteRange)) {
                    Plan.AlreadyComplete -> {
                        fileProgress.started(resumeFrom, resumeFrom)
                        promote(partFile, target)
                        null
                    }
                    is Plan.Restart -> plan.reason
                    is Plan.Copy -> {
                        val resumed = if (plan.append) resumeFrom else 0L
                        fileProgress.started(plan.expectedTotal, resumed)
                        copyBody(response, partFile, plan.append, fileProgress)
                        checkComplete(url, partFile, plan.expectedTotal)
                        promote(partFile, target)
                        null
                    }
                }
            }
        if (restartReason == null) return
        partFile.delete()
        if (!mayRestart) throw IOException("Cannot resume $url: $restartReason")
        fetch(url, target, fileProgress, byteRange, mayRestart = false)
    }

    @Suppress("ReturnCount") // One early answer per kind of response.
    private fun planFor(
        response: Response,
        url: String,
        resumeFrom: Long,
        byteRange: ByteRange?,
    ): Plan {
        val range = parseContentRange(response.header("Content-Range"))
        // Where in the whole file the bytes we hold end, which is where the response must begin.
        val responseStart = byteRange?.responseStart(resumeFrom) ?: resumeFrom
        if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
            val partIsWhole = resumeFrom > 0L && range.total == responseStart
            return if (partIsWhole) Plan.AlreadyComplete else Plan.Restart("range rejected")
        }
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        if (byteRange != null && response.code != HTTP_PARTIAL_CONTENT) {
            // A 200 would send the whole surah file into a file that must hold one ayah.
            throw IOException(
                "Expected 206 Partial Content for ${byteRange.requestHeader(resumeFrom)} of " +
                    "$url but got HTTP ${response.code}",
            )
        }

        val contentLength = response.body.contentLength().takeIf { it >= 0L }
        if (response.code != HTTP_PARTIAL_CONTENT) return Plan.Copy(false, contentLength)
        if (range.start != responseStart) return Plan.Restart("range starts at ${range.start}")
        // The total in Content-Range is the whole surah file's, not the clip's.
        val expectedTotal =
            if (byteRange != null) contentLength?.plus(resumeFrom)
            else range.total ?: contentLength?.plus(resumeFrom)
        return Plan.Copy(true, expectedTotal)
    }

    private suspend fun copyBody(
        response: Response,
        partFile: File,
        append: Boolean,
        fileProgress: FileProgress,
    ) {
        FileOutputStream(partFile, append).use { output ->
            response.body.byteStream().use { input -> copyStream(input, output, fileProgress) }
        }
    }

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        fileProgress: FileProgress,
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            currentCoroutineContext().ensureActive()
            val readBytes = input.read(buffer)
            if (readBytes < 0) return
            output.write(buffer, 0, readBytes)
            fileProgress.written(readBytes.toLong())
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

    // region Names and sizes

    /**
     * True when [reciterId] has one file per ayah, or has surah files from which each ayah is
     * fetched as a clip; false when it has only surah files.
     */
    override suspend fun hasAyahFiles(reciterId: String): Boolean = usesAyahFiles(reciterId)

    private suspend fun usesAyahFiles(reciterId: String): Boolean {
        val reciter = reciters.reciters().firstOrNull { it.id == reciterId } ?: return true
        return reciter.ayahUrlPattern != null || usesClips(reciterId)
    }

    /**
     * True when [reciterId] has only surah files but its timing pack says which bytes of them hold
     * each ayah. Ayah 1:1 stands for the reciter: a pack either measures its bytes or does not.
     */
    private suspend fun usesClips(reciterId: String): Boolean {
        val reciter = reciters.reciters().firstOrNull { it.id == reciterId } ?: return false
        val hasOnlySurahFiles = reciter.ayahUrlPattern == null
        return hasOnlySurahFiles &&
            reciters.ayahAudio(reciterId, AyahRef(1, 1))?.canBeFetchedAsClip == true
    }

    private fun fileNameFor(ayahFiles: Boolean, ayah: AyahRef): String =
        if (ayahFiles) {
            AudioUrls.padded(ayah.surah) + AudioUrls.padded(ayah.ayah) + ".mp3"
        } else {
            AudioUrls.padded(ayah.surah) + ".mp3"
        }

    private suspend fun estimateAyahFiles(reciterId: String, ayahs: List<AyahRef>): Long? {
        val sample = spread(ayahs, ESTIMATE_AYAH_SAMPLE)
        val timed = sample.mapNotNull { reciters.ayahAudio(reciterId, it) }
        val withDuration = timed.filter { it.durationMs != null }
        if (withDuration.isEmpty()) return estimateByMeanFileSize(reciterId, sample, ayahs.size)

        val rate = bytesPerMillisecond(reciterId, withDuration.first())
        val meanDurationMs = withDuration.map { checkNotNull(it.durationMs) }.average()
        return (meanDurationMs * rate * ayahs.size).toLong()
    }

    private fun bytesPerMillisecond(reciterId: String, timed: AyahAudio): Double {
        bytesPerMillisecond[reciterId]?.let {
            return it
        }
        val length = headLength(timed.url)
        val rate = length.toDouble() / checkNotNull(timed.durationMs)
        bytesPerMillisecond[reciterId] = rate
        return rate
    }

    private suspend fun estimateByMeanFileSize(
        reciterId: String,
        sample: List<AyahRef>,
        count: Int,
    ): Long? {
        val lengths = sample.mapNotNull { ayah ->
            resolver.resolve(reciterId, ayah)?.source?.url?.let { headLength(it) }
        }
        return if (lengths.isEmpty()) null else (lengths.average() * count).toLong()
    }

    /**
     * The size of the clips of [ayahs]: the bytes between the first and last byte of each, and for
     * the last ayah of a surah (which has no last byte) the mean of a few surah files' sizes less
     * where the ayah starts. Ayahs with no byte data are left out.
     */
    private suspend fun estimateClips(reciterId: String, ayahs: List<AyahRef>): Long? {
        val clips =
            ayahs
                .mapNotNull { reciters.ayahAudio(reciterId, it) }
                .filter {
                    it.canBeFetchedAsClip
                }
        if (clips.isEmpty()) return null
        val (closed, openEnded) = clips.partition { it.endByte != null }
        val closedBytes = closed.sumOf { checkNotNull(it.endByte) - checkNotNull(it.startByte) }
        return closedBytes + estimateOpenEndedClips(openEnded)
    }

    /** Measures a few surah files and scales their mean clip size to all of [openEnded]. */
    private fun estimateOpenEndedClips(openEnded: List<AyahAudio>): Long {
        val sample = spread(openEnded, ESTIMATE_SURAH_SAMPLE)
        if (sample.isEmpty()) return 0L
        val clipSizes = sample.map { headLength(it.url) - checkNotNull(it.startByte) }
        return (clipSizes.average() * openEnded.size).toLong()
    }

    private suspend fun estimateSurahFiles(reciterId: String, ayahs: List<AyahRef>): Long? {
        val surahs = ayahs.map { it.surah }.distinct()
        val sample = spread(surahs, ESTIMATE_SURAH_SAMPLE)
        val lengths = sample.mapNotNull { surah ->
            resolver.resolve(reciterId, AyahRef(surah, 1))?.source?.url?.let { headLength(it) }
        }
        return if (lengths.isEmpty()) null else (lengths.average() * surahs.size).toLong()
    }

    /** Returns up to [count] items of [items] spread evenly from first to last. */
    private fun <T> spread(items: List<T>, count: Int): List<T> {
        if (items.size <= count) return items
        return (0 until count).map { items[it * (items.size - 1) / (count - 1)] }
    }

    /**
     * Asks for the size of the file at [url] without downloading it, trying the fallback domain
     * too. Throws [IOException] when neither answers with a length.
     */
    private fun headLength(url: String): Long {
        for (candidate in listOfNotNull(url, AudioUrls.fallbackUrl(url))) {
            try {
                val request = Request.Builder().url(candidate).head().build()
                val length =
                    client.newCall(request).execute().use { response ->
                        response
                            .takeIf { it.isSuccessful }
                            ?.body
                            ?.contentLength()
                            ?.takeIf { it >= 0L }
                    }
                if (length != null) return length
            } catch (@Suppress("SwallowedException") unreachable: IOException) {
                continue
            }
        }
        throw IOException("Cannot read the size of $url")
    }

    // endregion

    private companion object {
        const val ESTIMATE_AYAH_SAMPLE = 24
        const val ESTIMATE_SURAH_SAMPLE = 3
        val AYAH_FILE = Regex("\\d{6}\\.mp3")
        val CONTENT_RANGE = Regex("bytes (?:(\\d+)-\\d+|\\*)/(\\d+|\\*)")
    }
}
