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

interface AudioDownloads {

    /** Emits the download state of one surah of one reciter, again after every change. */
    fun state(reciterId: String, surah: Int): Flow<DownloadState>

    /** Downloads the audio of [ayahs] so they play offline. */
    suspend fun downloadPage(reciterId: String, ayahs: List<AyahRef>)

    suspend fun downloadSurah(reciterId: String, surah: Int)

    /** Returns the bytes of downloaded audio held for [reciterId]. */
    suspend fun bytesUsed(reciterId: String): Long

    /** Deletes all downloaded audio of [reciterId]. */
    suspend fun remove(reciterId: String)
}
