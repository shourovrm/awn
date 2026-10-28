package app.awn.quran.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val COPY_BUFFER_BYTES = 64 * 1024
private const val PERCENT = 100

/** The downloaded file is not the one the release lists. */
internal class ChecksumMismatchException(fileName: String) :
    IOException("The SHA-256 of $fileName does not match SHA256SUMS.txt")

/** Saves a release APK into [directory] and checks it before it is offered for install. */
internal class ApkDownloader(
    private val httpClient: OkHttpClient,
    private val releaseClient: ReleaseClient,
    private val directory: File,
) {

    /**
     * Returns the verified file. A partial or rejected file is deleted, also when the coroutine is
     * cancelled. [onPercent] gets 0 to 100.
     */
    suspend fun download(
        release: AppRelease,
        apk: ReleaseAsset,
        versionName: String,
        onPercent: (Int) -> Unit,
    ): File {
        val target = File(directory, "awn-$versionName.apk")
        var isComplete = false
        try {
            withContext(Dispatchers.IO) {
                val expectedHash = expectedHashOf(release, apk)
                directory.mkdirs()
                copyToFile(apk, target, onPercent)
                if (sha256Hex(target) != expectedHash) {
                    throw ChecksumMismatchException(apk.name)
                }
            }
            isComplete = true
        } finally {
            // Also runs on cancellation, so a stopped download leaves no partial file.
            if (!isComplete) target.delete()
        }
        return target
    }

    private suspend fun expectedHashOf(release: AppRelease, apk: ReleaseAsset): String {
        val checksums = release.assets.firstOrNull { it.name == CHECKSUMS_ASSET_NAME }
        val checksumText = checksums?.let { releaseClient.fetchText(it.downloadUrl) }
        return requiredChecksum(checksumText, apk.name)
    }

    private suspend fun copyToFile(apk: ReleaseAsset, target: File, onPercent: (Int) -> Unit) {
        val request = Request.Builder().url(apk.downloadUrl).build()
        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("${apk.name} answered ${response.code}")
            val totalBytes = response.body.contentLength()
            val copiedBytes =
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        copyWithProgress(input, output, totalBytes, onPercent)
                    }
                }
            // A connection that closes early ends the stream without an error.
            if (totalBytes > 0 && copiedBytes != totalBytes) {
                throw IOException("${apk.name}: got $copiedBytes of $totalBytes bytes")
            }
        }
    }
}

private suspend fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    onPercent: (Int) -> Unit,
): Long {
    var copiedBytes = 0L
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    while (true) {
        currentCoroutineContext().ensureActive()
        val readBytes = input.read(buffer)
        if (readBytes < 0) return copiedBytes
        output.write(buffer, 0, readBytes)
        copiedBytes += readBytes
        if (totalBytes > 0) onPercent((copiedBytes * PERCENT / totalBytes).toInt())
    }
}

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes < 0) break
            digest.update(buffer, 0, readBytes)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
