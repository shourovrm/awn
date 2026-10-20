package app.awn.quran.player

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.awn.quran.core.data.AyahRef
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * An [AudioDownloadRunner] that hands the download to [AudioDownloadWorker] and waits for it,
 * reporting the worker's progress. Cancelling the waiting coroutine cancels the work. The work
 * itself survives the screen closing and the process dying; the app must have called
 * [AudioDownloadWork.install].
 *
 * Downloads queue one after another under one unique work name, matching the downloader's own
 * one-at-a-time lock.
 */
class WorkManagerDownloadRunner(context: Context) : AudioDownloadRunner {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val jobDirectory = File(appContext.filesDir, JOB_DIRECTORY)

    override suspend fun run(
        reciterId: String,
        ayahs: List<AyahRef>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        deleteStaleJobFiles()
        val jobFile = File(jobDirectory, "${System.nanoTime()}.txt")
        AudioDownloadJob(reciterId, ayahs).write(jobFile)
        val request =
            OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(workDataOf(AudioDownloadWorker.KEY_JOB_FILE to jobFile.path))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        try {
            val finished =
                workManager.getWorkInfoByIdFlow(request.id).first { info ->
                    reportProgress(info, onProgress)
                    info != null && info.state.isFinished
                }
            checkOutcome(checkNotNull(finished))
        } catch (cancelled: CancellationException) {
            workManager.cancelWorkById(request.id)
            throw cancelled
        }
    }

    private fun reportProgress(info: WorkInfo?, onProgress: (DownloadProgress) -> Unit) {
        val progress = info?.progress ?: return
        val done =
            progress.getLong(AudioDownloadWorker.KEY_DONE_BYTES, AudioDownloadWorker.UNKNOWN_BYTES)
        if (done == AudioDownloadWorker.UNKNOWN_BYTES) return
        val total =
            progress.getLong(AudioDownloadWorker.KEY_TOTAL_BYTES, AudioDownloadWorker.UNKNOWN_BYTES)
        onProgress(DownloadProgress(done, total.takeIf { it != AudioDownloadWorker.UNKNOWN_BYTES }))
    }

    private fun checkOutcome(finished: WorkInfo) {
        when (finished.state) {
            WorkInfo.State.SUCCEEDED -> Unit
            WorkInfo.State.CANCELLED -> throw CancellationException("Download cancelled")
            else -> {
                val fields =
                    finished.outputData.keyValueMap
                        .mapNotNull { (key, value) ->
                            (value as? Long)?.let { key to it }
                        }
                        .toMap()
                throw DownloadFailedException(
                    DownloadProblem.fromFields(fields) ?: DownloadProblem.ServerNotReachable,
                )
            }
        }
    }

    /**
     * Job files of work that ended without cleaning up (cancelled work keeps its file to resume).
     */
    private fun deleteStaleJobFiles() {
        val oldestKeptMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(STALE_AFTER_DAYS)
        jobDirectory
            .listFiles()
            .orEmpty()
            .filter { it.lastModified() < oldestKeptMs }
            .forEach {
                it.delete()
            }
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "awn-audio-download"
        const val JOB_DIRECTORY = "audio-jobs"
        const val STALE_AFTER_DAYS = 2L
    }
}
