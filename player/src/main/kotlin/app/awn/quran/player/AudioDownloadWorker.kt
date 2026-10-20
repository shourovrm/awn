package app.awn.quran.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.awn.quran.core.data.AyahRef
import java.io.File
import java.io.IOException

/**
 * Downloads reciter audio in WorkManager, as a foreground service with a progress notification, so
 * a download continues with the screen off and starts again after the process dies. Partial files
 * are kept and continued, so a restart loses nothing.
 *
 * What to download is written to a job file (see [AudioDownloadJob]) because a work request carries
 * at most 10 KB and a whole-Quran download names 6 236 ayahs. The worker finds the downloader
 * through [AudioDownloadWork.install], which the app calls when it starts, before WorkManager runs
 * any work.
 */
class AudioDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    @Suppress("ReturnCount") // Guard clauses for a worker that cannot run, then the outcomes.
    override suspend fun doWork(): Result {
        val environment = AudioDownloadWork.environment ?: return Result.retry()
        val jobPath = inputData.getString(KEY_JOB_FILE) ?: return Result.failure()
        val job = AudioDownloadJob.read(File(jobPath)) ?: return Result.failure()
        setForeground(foregroundInfo(DownloadProgress(0L, null)))
        return try {
            environment.downloads.requireFreeSpace(job.reciterId, job.ayahs, environment.freeSpace)
            var lastReportMs = 0L
            environment.downloads.download(job.reciterId, job.ayahs) { progress ->
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastReportMs >= REPORT_INTERVAL_MS) {
                    lastReportMs = nowMs
                    reportProgress(progress)
                }
            }
            File(jobPath).delete()
            Result.success()
        } catch (failed: IOException) {
            File(jobPath).delete()
            val problem = DownloadProblem.classify(failed, environment.network.isOnline())
            Result.failure(Data.Builder().putAll(problem.toFields()).build())
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(DownloadProgress(0L, null))

    private fun reportProgress(progress: DownloadProgress) {
        setProgressAsync(
            workDataOf(
                KEY_DONE_BYTES to progress.doneBytes,
                KEY_TOTAL_BYTES to (progress.totalBytes ?: UNKNOWN_BYTES),
            ),
        )
        notificationManager().notify(NOTIFICATION_ID, notification(progress))
    }

    private fun foregroundInfo(progress: DownloadProgress): ForegroundInfo {
        createChannel()
        val notification = notification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(progress: DownloadProgress): android.app.Notification {
        val total = progress.totalBytes
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        return android.app.Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                PROGRESS_SCALE,
                if (total != null && total > 0L) {
                    (progress.doneBytes * PROGRESS_SCALE / total)
                        .toInt()
                        .coerceIn(0, PROGRESS_SCALE)
                } else {
                    0
                },
                total == null || total <= 0L,
            )
            .addAction(
                android.app.Notification.Action.Builder(
                        null,
                        applicationContext.getString(R.string.download_notification_cancel),
                        cancelIntent,
                    )
                    .build(),
            )
            .build()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    companion object {
        const val KEY_JOB_FILE = "audio_job_file"
        const val KEY_DONE_BYTES = "audio_done_bytes"
        const val KEY_TOTAL_BYTES = "audio_total_bytes"
        const val UNKNOWN_BYTES = -1L
        private const val CHANNEL_ID = "audio_downloads"
        private const val NOTIFICATION_ID = 4_201
        private const val PROGRESS_SCALE = 1_000
        private const val REPORT_INTERVAL_MS = 500L
    }
}

/** What [AudioDownloadWorker] needs from the app; set once by [AudioDownloadWork.install]. */
class AudioDownloadEnvironment(
    val downloads: AudioDownloads,
    val freeSpace: FreeSpace,
    val network: NetworkStatus,
)

object AudioDownloadWork {

    @Volatile
    internal var environment: AudioDownloadEnvironment? = null
        private set

    /**
     * Gives the worker the app's downloader. Call it in `Application.onCreate`, so that work
     * WorkManager restarts after the process died finds it.
     */
    fun install(downloads: AudioDownloads, freeSpace: FreeSpace, network: NetworkStatus) {
        environment = AudioDownloadEnvironment(downloads, freeSpace, network)
    }
}

/** The reciter and ayahs of one download, saved in a file that outlives the process. */
internal class AudioDownloadJob(val reciterId: String, val ayahs: List<AyahRef>) {

    fun write(file: File) {
        file.parentFile?.mkdirs()
        val lines = listOf(reciterId) + ayahs.map { "${it.surah}:${it.ayah}" }
        file.writeText(lines.joinToString("\n"))
    }

    companion object {
        @Suppress("ReturnCount") // Guard clauses for a missing or empty job file.
        fun read(file: File): AudioDownloadJob? {
            if (!file.isFile) return null
            val lines = file.readLines().filter { it.isNotBlank() }
            val reciterId = lines.firstOrNull() ?: return null
            val ayahs =
                lines.drop(1).map { line ->
                    val (surah, ayah) = line.split(':')
                    AyahRef(surah.toInt(), ayah.toInt())
                }
            return AudioDownloadJob(reciterId, ayahs)
        }
    }
}
