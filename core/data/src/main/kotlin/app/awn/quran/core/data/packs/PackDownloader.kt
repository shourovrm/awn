package app.awn.quran.core.data.packs

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A pack could not be downloaded, verified or installed; [fileName] says which file. [reason] is
 * the cause in the user's terms. For [PackFailureReason.NoSpace], [neededBytes] and [freeBytes]
 * hold the numbers to show.
 */
class PackInstallException(
    val fileName: String,
    message: String,
    cause: Throwable? = null,
    val reason: PackFailureReason = PackFailureReason.Other,
    val neededBytes: Long? = null,
    val freeBytes: Long? = null,
) : IOException("$fileName: $message", cause)

/**
 * Downloads one file with HTTP Range resume. Call it from a background dispatcher: every method
 * blocks on the network.
 */
class PackDownloader(
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
) {

    /**
     * Fills [partFile] with the bytes of [url]. When [partFile] already holds the first N bytes
     * from an earlier attempt, only the rest is requested. [onBytes] receives the bytes written so
     * far and the total size, which is [expectedBytes] when the server does not say.
     */
    suspend fun download(
        url: String,
        partFile: File,
        fileName: String,
        expectedBytes: Long,
        onBytes: (written: Long, total: Long) -> Unit,
    ) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "$fileName: the download address must be http or https but was $url."
        }
        partFile.parentFile?.mkdirs()
        val alreadyHave = if (partFile.isFile) partFile.length() else 0L
        if (expectedBytes > 0 && alreadyHave > expectedBytes) {
            partFile.delete()
        }
        try {
            downloadFrom(url, partFile, fileName, expectedBytes, onBytes, restartAllowed = true)
        } catch (error: PackInstallException) {
            throw error
        } catch (error: IOException) {
            throw PackInstallException(
                fileName,
                "download failed: ${error.message}",
                error,
                PackFailureReason.of(error),
            )
        }
    }

    @Suppress("LongParameterList", "ThrowsCount") // One recursive step with all request state.
    private suspend fun downloadFrom(
        url: String,
        partFile: File,
        fileName: String,
        expectedBytes: Long,
        onBytes: (Long, Long) -> Unit,
        restartAllowed: Boolean,
    ) {
        val resumeFrom = if (partFile.isFile) partFile.length() else 0L
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            // gzip would change the byte counts that Range and the size check depend on.
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                    if (!contentRange.startsWith("bytes $resumeFrom-")) {
                        throw PackInstallException(
                            fileName,
                            "server resumed at '$contentRange' but $resumeFrom bytes were requested",
                        )
                    }
                    copyBody(connection, partFile, fileName, resumeFrom, expectedBytes, onBytes)
                }
                HttpURLConnection.HTTP_OK ->
                    // The server ignored the Range header, so the body is the whole file.
                    copyBody(connection, partFile, fileName, 0L, expectedBytes, onBytes)
                RANGE_NOT_SATISFIABLE -> {
                    // The partial file is already as long as the file, or longer than it.
                    if (resumeFrom == expectedBytes) return
                    if (!restartAllowed) {
                        throw PackInstallException(fileName, "server answered 416 after restart")
                    }
                    partFile.delete()
                    downloadFrom(url, partFile, fileName, expectedBytes, onBytes, false)
                }
                else ->
                    throw PackInstallException(
                        fileName,
                        "server answered HTTP $status for $url",
                        reason = PackFailureReason.ServerError,
                    )
            }
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("LongParameterList", "NestedBlockDepth") // Streaming loop inside two resource scopes.
    private suspend fun copyBody(
        connection: HttpURLConnection,
        partFile: File,
        fileName: String,
        startOffset: Long,
        expectedBytes: Long,
        onBytes: (Long, Long) -> Unit,
    ) {
        val bodyLength = connection.contentLengthLong
        val total =
            when {
                expectedBytes > 0 -> expectedBytes
                bodyLength >= 0 -> startOffset + bodyLength
                else -> -1L
            }
        var written = startOffset
        RandomAccessFile(partFile, "rw").use { output ->
            output.setLength(startOffset)
            output.seek(startOffset)
            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val readCount = input.read(buffer)
                    if (readCount < 0) break
                    output.write(buffer, 0, readCount)
                    written += readCount
                    onBytes(written, total)
                }
            }
        }
        if (bodyLength >= 0 && written != startOffset + bodyLength) {
            throw PackInstallException(
                fileName,
                "connection ended after $written bytes",
                reason = PackFailureReason.NoInternet,
            )
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
        private const val RANGE_NOT_SATISFIABLE = 416
        private const val BUFFER_BYTES = 64 * 1024

        /** Returns the lowercase hex SHA-256 of [file]. */
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val readCount = input.read(buffer)
                    if (readCount < 0) break
                    digest.update(buffer, 0, readCount)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
