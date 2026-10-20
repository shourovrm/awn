package app.awn.quran.player

import android.content.Context
import android.os.storage.StorageManager
import app.awn.quran.core.data.AyahRef
import java.io.File

/**
 * Runs one download of audio and reports its progress. [DirectDownloadRunner] downloads in the
 * calling coroutine; [WorkManagerDownloadRunner] hands the work to WorkManager so it continues with
 * the screen off and resumes after the process dies. Both throw `IOException` on failure and keep
 * partial files, and cancelling the coroutine cancels the download.
 */
fun interface AudioDownloadRunner {

    suspend fun run(reciterId: String, ayahs: List<AyahRef>, onProgress: (DownloadProgress) -> Unit)
}

/** How many bytes the phone can still store where audio is kept. */
fun interface FreeSpace {

    fun bytes(): Long

    companion object {
        val Unlimited = FreeSpace { Long.MAX_VALUE }
    }
}

/**
 * The space the system will give [directory]'s volume, which counts cached files of other apps that
 * Android clears on demand, so a nearly full phone is not refused needlessly. Works even before the
 * directory exists.
 */
class DirectoryFreeSpace(context: Context, private val directory: File) : FreeSpace {

    private val storageManager =
        context.applicationContext.getSystemService(StorageManager::class.java)

    override fun bytes(): Long {
        val existing = generateSequence(directory) { it.parentFile }.firstOrNull { it.exists() }
        existing ?: return 0L
        return storageManager.getAllocatableBytes(storageManager.getUuidForPath(existing))
    }
}

/**
 * Throws [InsufficientSpaceException] with both numbers when what is still missing of [ayahs] does
 * not fit in [freeSpace]. Nothing is thrown when the size cannot be found out (offline): the
 * download itself then fails with the real cause.
 */
suspend fun AudioDownloads.requireFreeSpace(
    reciterId: String,
    ayahs: List<AyahRef>,
    freeSpace: FreeSpace,
) {
    val estimate = estimateBytes(reciterId, ayahs) ?: return
    val neededBytes = (estimate - status(reciterId, ayahs).presentBytes).coerceAtLeast(0L)
    val freeBytes = freeSpace.bytes()
    if (neededBytes > freeBytes) throw InsufficientSpaceException(neededBytes, freeBytes)
}

class DirectDownloadRunner(
    private val downloads: AudioDownloads,
    private val freeSpace: FreeSpace = FreeSpace.Unlimited,
) : AudioDownloadRunner {

    override suspend fun run(
        reciterId: String,
        ayahs: List<AyahRef>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        downloads.requireFreeSpace(reciterId, ayahs, freeSpace)
        downloads.download(reciterId, ayahs, onProgress)
    }
}
