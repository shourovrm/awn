package app.awn.quran.core.data.models

import app.awn.quran.core.data.downloads.DownloadNetworkRule
import app.awn.quran.core.data.packs.PackDownloader
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.data.packs.PackInstallException
import app.awn.quran.core.data.packs.usableBytesOnDisk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One file of the speech model: its release asset name, exact size and SHA-256. */
data class SpeechModelFile(val name: String, val bytes: Long, val sha256: String)

/**
 * The files of the speech model, downloaded after install from this repository's `models-v1`
 * release. The hashes are the ones in `gradle/models.sha256` (a unit test compares them), so the
 * build, the tracker tests and the app all trust the same bytes.
 */
object SpeechModelSpec {

    const val MODEL_FILE_NAME = "zipformer_p_arabic_v3.int8.onnx"
    const val TOKENS_FILE_NAME = "tokens.txt"
    const val REFERENCE_PHONEMES_FILE_NAME = "ordered_quran_phonemes.json"

    const val RELEASE_URL = "https://github.com/shourovrm/awn/releases/download/models-v1"

    /** Smallest first, so a broken connection shows up before the 72 MB file starts. */
    val files =
        listOf(
            SpeechModelFile(
                TOKENS_FILE_NAME,
                2_597L,
                "ed1cf15923b96a8c6fb4b2e03791d1ec2e5d09aa2dac99086e1b3175986a581f",
            ),
            SpeechModelFile(
                REFERENCE_PHONEMES_FILE_NAME,
                12_827_409L,
                "af54b04918cb46873dc9f7c9bac48be3b9f0a41d57be8f1659cdd2d8f09e5c50",
            ),
            SpeechModelFile(
                MODEL_FILE_NAME,
                72_705_392L,
                "6a5ddafa9c5e5c01260d30264031b341785bdc152e9ef1d569b41c8c278508eb",
            ),
        )
}

/** What the speech model looks like to the screens that offer, show and remove it. */
sealed interface SpeechModelState {

    /** The files are not on the phone (or only part of them is). */
    data object NotInstalled : SpeechModelState

    /** [downloadedBytes] of [totalBytes] are on the phone, counting all files of the model. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : SpeechModelState {
        val progress: Float
            get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }

    data object Installed : SpeechModelState

    /**
     * The last download stopped. [reason] says why in the user's terms; for
     * [PackFailureReason.NoSpace], [neededBytes] and [freeBytes] hold the numbers to show. The
     * model is not installed; calling download again resumes where this stopped.
     */
    data class Failed(
        val reason: PackFailureReason,
        val message: String,
        val neededBytes: Long? = null,
        val freeBytes: Long? = null,
    ) : SpeechModelState
}

/** The three files sherpa-onnx and the tracker read, once the model is installed. */
data class SpeechModelFiles(val model: File, val tokens: File, val referencePhonemes: File)

/**
 * Downloads, verifies and removes the speech model in [directory]. A download writes each file to
 * `.downloads/<name>.part` (resuming a part left by an earlier attempt with an HTTP Range request),
 * checks its size and SHA-256, and only then renames it into place, so [isInstalled] never sees a
 * half-written file. A failure ends in [SpeechModelState.Failed] and is never swallowed; the user
 * can try again.
 *
 * Files already in [directory] with the exact size count as installed: earlier app versions copied
 * the same verified files out of the APK into this folder.
 */
@Suppress("TooManyFunctions", "LongParameterList")
// One small function per step of the download; every parameter after scope is a test seam with a
// production default.
class SpeechModelStore(
    private val directory: File,
    private val scope: CoroutineScope,
    private val files: List<SpeechModelFile> = SpeechModelSpec.files,
    private val baseUrl: String = SpeechModelSpec.RELEASE_URL,
    private val downloader: PackDownloader = PackDownloader(),
    private val usableBytesOf: (File) -> Long = ::usableBytesOnDisk,
    private val networkRule: DownloadNetworkRule = DownloadNetworkRule.Unrestricted,
) {

    private val partsDirectory = File(directory, ".downloads")
    private val mutableState = MutableStateFlow(stateOfFilesOnDisk())
    private var downloadJob: Job? = null

    val state: StateFlow<SpeechModelState> = mutableState.asStateFlow()

    /** The download size of the whole model, for "needs N MB". */
    val totalBytes: Long = files.sumOf { it.bytes }

    fun isInstalled(): Boolean = files.all(::isInPlace)

    /** The model files, or null while any of them is missing. */
    fun installedFiles(): SpeechModelFiles? {
        if (!isInstalled()) return null
        return SpeechModelFiles(
            model = File(directory, SpeechModelSpec.MODEL_FILE_NAME),
            tokens = File(directory, SpeechModelSpec.TOKENS_FILE_NAME),
            referencePhonemes = File(directory, SpeechModelSpec.REFERENCE_PHONEMES_FILE_NAME),
        )
    }

    /**
     * Starts the download unless one is running or the model is installed, and returns its job.
     * Progress and the outcome appear in [state].
     */
    @Synchronized
    fun download(): Job {
        val running = downloadJob
        if (running != null && running.isActive) return running
        val job =
            scope.launch(Dispatchers.IO) {
                try {
                    downloadMissingFiles()
                } catch (cancelled: CancellationException) {
                    // The part files stay on disk so the next attempt resumes.
                    mutableState.value = stateOfFilesOnDisk()
                    throw cancelled
                }
            }
        downloadJob = job
        return job
    }

    /** Stops a running download. The unfinished files stay for the next attempt. */
    @Synchronized
    fun cancel() {
        downloadJob?.cancel()
    }

    /**
     * Deletes the model and any unfinished download of it. A file that cannot be deleted leaves
     * [SpeechModelState.Failed] behind instead of an exception, because the caller is a button.
     */
    suspend fun remove() {
        cancel()
        downloadJob?.join()
        val failure =
            withContext(Dispatchers.IO) {
                try {
                    for (file in files) {
                        val installed = File(directory, file.name)
                        if (installed.exists() && !installed.delete()) {
                            throw IOException("Could not delete ${installed.path}.")
                        }
                    }
                    partsDirectory.deleteRecursively()
                    null
                } catch (problem: IOException) {
                    problem
                }
            }
        mutableState.value =
            if (failure == null) {
                SpeechModelState.NotInstalled
            } else {
                SpeechModelState.Failed(PackFailureReason.Other, failure.message.orEmpty())
            }
    }

    private suspend fun downloadMissingFiles() {
        val missing = files.filterNot(::isInPlace)
        if (missing.isEmpty()) {
            mutableState.value = SpeechModelState.Installed
            return
        }
        if (!canStart(missing)) return
        var finishedBytes = files.filter(::isInPlace).sumOf { it.bytes }
        mutableState.value = SpeechModelState.Downloading(finishedBytes, totalBytes)
        try {
            for (file in missing) {
                val before = finishedBytes
                downloadOne(file) { written ->
                    mutableState.value = SpeechModelState.Downloading(before + written, totalBytes)
                }
                finishedBytes += file.bytes
            }
            mutableState.value = SpeechModelState.Installed
        } catch (failure: PackInstallException) {
            // A blocking read does not notice a cancel, so a cancelled download can end here.
            currentCoroutineContext().ensureActive()
            mutableState.value = SpeechModelState.Failed(failure.reason, failure.message.orEmpty())
        } catch (failure: IOException) {
            currentCoroutineContext().ensureActive()
            mutableState.value =
                SpeechModelState.Failed(
                    PackFailureReason.of(failure),
                    "Could not install the speech model: ${failure.message}",
                )
        }
    }

    // Reports why the download cannot start and returns false: Wi-Fi only on mobile data, or no
    // room.
    private fun canStart(missing: List<SpeechModelFile>): Boolean {
        if (networkRule.mayDownload()) return hasRoomFor(missing)
        mutableState.value =
            SpeechModelState.Failed(
                reason = PackFailureReason.WifiOnly,
                message = "Downloads are limited to Wi-Fi and the phone is on mobile data.",
            )
        return false
    }

    // Reports the failure and returns false when the phone cannot hold what is still to fetch.
    private fun hasRoomFor(missing: List<SpeechModelFile>): Boolean {
        val needed = missing.sumOf { it.bytes - partialBytesOf(it) }
        directory.mkdirs()
        val free = usableBytesOf(directory)
        if (free >= needed) return true
        mutableState.value =
            SpeechModelState.Failed(
                reason = PackFailureReason.NoSpace,
                message = "The speech model needs $needed bytes but only $free are free.",
                neededBytes = needed,
                freeBytes = free,
            )
        return false
    }

    private fun partialBytesOf(file: SpeechModelFile): Long =
        File(partsDirectory, "${file.name}.part").let { if (it.isFile) it.length() else 0L }

    private suspend fun downloadOne(file: SpeechModelFile, onBytesWritten: (Long) -> Unit) {
        val part = File(partsDirectory, "${file.name}.part")
        downloader.download(
            url = "${baseUrl.trimEnd('/')}/${file.name}",
            partFile = part,
            fileName = file.name,
            expectedBytes = file.bytes,
        ) { written, _ ->
            onBytesWritten(written)
        }
        verifyOrDiscard(file, part)
        directory.mkdirs()
        // Same folder tree, so the rename is atomic: a reader sees the whole file or none of it.
        Files.move(
            part.toPath(),
            File(directory, file.name).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun verifyOrDiscard(file: SpeechModelFile, part: File) {
        if (part.length() != file.bytes) {
            val received = part.length()
            part.delete()
            throw PackInstallException(
                file.name,
                "expected ${file.bytes} bytes but received $received",
                reason = PackFailureReason.Damaged,
            )
        }
        val actualHash = PackDownloader.sha256Of(part)
        if (actualHash != file.sha256) {
            part.delete()
            throw PackInstallException(
                file.name,
                "SHA-256 mismatch: expected ${file.sha256} but got $actualHash; download discarded",
                reason = PackFailureReason.Damaged,
            )
        }
    }

    private fun isInPlace(file: SpeechModelFile): Boolean =
        File(directory, file.name).let { it.isFile && it.length() == file.bytes }

    private fun stateOfFilesOnDisk(): SpeechModelState =
        if (isInstalled()) SpeechModelState.Installed else SpeechModelState.NotInstalled
}
