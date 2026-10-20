package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.Flow

/** [totalBytes] is null until the size of the surah audio is known. */
data class DownloadState(
    val reciterId: String,
    val surah: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val complete: Boolean,
)

/**
 * Progress of one [AudioDownloads.download] call. [totalBytes] is null until a file size is known.
 */
data class DownloadProgress(val doneBytes: Long, val totalBytes: Long?)

/** What is on disk for a list of ayahs: [presentAyahs] of [totalAyahs] take [presentBytes]. */
data class AudioStatus(val presentBytes: Long, val presentAyahs: Int, val totalAyahs: Int) {
    val complete: Boolean
        get() = totalAyahs > 0 && presentAyahs >= totalAyahs
}

interface AudioDownloads {

    /** Emits the download state of one surah of one reciter, again after every change. */
    fun state(reciterId: String, surah: Int): Flow<DownloadState>

    /** Downloads the audio of [ayahs] so they play offline. */
    suspend fun downloadPage(reciterId: String, ayahs: List<AyahRef>)

    suspend fun downloadSurah(reciterId: String, surah: Int)

    /**
     * Downloads the audio of [ayahs], skipping files already present, and reports the byte count
     * through [onProgress] as it grows. Throws [DownloadNotAllowedException] when the network
     * policy refuses, and `IOException` when a file cannot be fetched; cancelling the coroutine
     * keeps the partial file so the next call continues it.
     */
    suspend fun download(
        reciterId: String,
        ayahs: List<AyahRef>,
        onProgress: (DownloadProgress) -> Unit = {},
    )

    /** Returns the bytes of downloaded audio held for [reciterId]. */
    suspend fun bytesUsed(reciterId: String): Long

    /**
     * Emits [bytesUsed] now and again after every file that finishes and after every delete, so a
     * list showing it never goes stale.
     */
    fun usedBytes(reciterId: String): Flow<Long>

    /** Returns how much of [ayahs] is on disk for [reciterId]. */
    suspend fun status(reciterId: String, ayahs: List<AyahRef>): AudioStatus

    /**
     * Returns the size in bytes that downloading [ayahs] takes, from the length of a few files of
     * [reciterId], or null when that cannot be found out (offline).
     */
    suspend fun estimateBytes(reciterId: String, ayahs: List<AyahRef>): Long?

    /** Deletes the downloaded audio of [ayahs]; a reciter with surah files loses those surahs. */
    suspend fun removeAyahs(reciterId: String, ayahs: List<AyahRef>)

    /** Deletes all downloaded audio of [reciterId]. */
    suspend fun remove(reciterId: String)
}
