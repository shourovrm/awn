package app.awn.quran.core.data.downloads

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Installs one pack in the background and shows a foreground notification with its progress, so the
 * download goes on when the user leaves the app.
 */
class PackInstallWorker(
    context: Context,
    parameters: WorkerParameters,
    private val runner: PackInstallRunner,
    private val displayNameOf: suspend (String) -> String,
) : CoroutineWorker(context, parameters) {

    private val packId: String
        get() =
            requireNotNull(inputData.getString(WorkManagerPackScheduler.KEY_PACK_ID)) {
                "The install work was queued without a pack id."
            }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfoFor(displayNameOf(packId), percent = 0)

    override suspend fun doWork(): Result {
        val title = displayNameOf(packId)
        startForeground(title)
        var lastPercent = -1
        val outcome =
            runner.run(packId) { progress ->
                val percent = (progress * PERCENT).toInt()
                // The callback runs once per 64 KB; update the screen only when the figure moves.
                if (percent != lastPercent) {
                    lastPercent = percent
                    setProgressAsync(workDataOf(WorkManagerPackScheduler.KEY_PROGRESS to progress))
                    setForegroundAsync(foregroundInfoFor(title, percent))
                }
            }
        return when (outcome) {
            PackInstallOutcome.Succeeded -> Result.success()
            PackInstallOutcome.RetryLater -> Result.retry()
            is PackInstallOutcome.Failed ->
                Result.failure(WorkManagerPackScheduler.failureData(outcome))
        }
    }

    /**
     * Starts the foreground notification. Android 12 and later refuse it when the work was started
     * from the background; the install then runs without it, within the normal time limit.
     */
    private suspend fun startForeground(title: String) {
        try {
            setForeground(foregroundInfoFor(title, percent = 0))
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException.
        }
    }

    private fun foregroundInfoFor(title: String, percent: Int): ForegroundInfo {
        val notification =
            PackNotifications.build(applicationContext, title, percent, cancelIntent())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationIdOf(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationIdOf(), notification)
        }
    }

    private fun cancelIntent() =
        WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

    private fun notificationIdOf(): Int = PackNotifications.idOf(packId)

    private companion object {
        const val PERCENT = 100
    }
}

/** Builds [PackInstallWorker]s with the runner of this process; see `AwnApplication`. */
class PackWorkerFactory(
    private val runner: PackInstallRunner,
    private val displayNameOf: suspend (String) -> String,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): PackInstallWorker? =
        if (workerClassName == PackInstallWorker::class.java.name) {
            PackInstallWorker(appContext, workerParameters, runner, displayNameOf)
        } else {
            // Null lets WorkManager's default factory handle its own and other workers.
            null
        }
}
