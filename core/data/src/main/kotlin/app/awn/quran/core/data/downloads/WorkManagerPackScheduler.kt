package app.awn.quran.core.data.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.awn.quran.core.data.packs.PackFailureReason
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Runs pack installs in WorkManager, so they continue when the user leaves the app and start again
 * when Android stops the process. Each pack is one unique work item named by [uniqueNameOf]; the
 * work is tagged so one query lists every install.
 */
class WorkManagerPackScheduler(
    context: Context,
    /** Read when work is queued: true limits the install to Wi-Fi. */
    private val isWifiOnly: () -> Boolean = { false },
) : PackWorkScheduler {

    // Created on first use: WorkManager is initialised by AwnApplication's Configuration.Provider,
    // which needs the services object that is still being built when this class is created.
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    override val transfers: Flow<Map<String, PackTransfer>>
        get() = workManager.getWorkInfosByTagFlow(WORK_TAG).map(::transfersOf)

    override fun enqueue(id: String) {
        val request =
            OneTimeWorkRequestBuilder<PackInstallWorker>()
                .setInputData(workDataOf(KEY_PACK_ID to id))
                .addTag(WORK_TAG)
                .addTag(idTagOf(id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkTypeFor(isWifiOnly()))
                        .build(),
                )
                // Starts at once while the app is open; the install itself shows a notification.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // KEEP: a second request for a pack that is already queued or running changes nothing.
        workManager.enqueueUniqueWork(uniqueNameOf(id), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(id: String) {
        workManager.cancelUniqueWork(uniqueNameOf(id))
    }

    override fun pruneFinished() {
        workManager.pruneWork()
    }

    companion object {
        const val WORK_TAG = "pack-install"
        const val KEY_PACK_ID = "pack_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_FAILURE_REASON = "failure_reason"
        const val KEY_NEEDED_BYTES = "needed_bytes"
        const val KEY_FREE_BYTES = "free_bytes"
        private const val ID_TAG_PREFIX = "pack-id:"
        private const val BACKOFF_SECONDS = 30L
        private const val NO_VALUE = -1L

        fun uniqueNameOf(id: String): String = "pack-install:$id"

        /** The network the install waits for: any connection, or Wi-Fi while limited to it. */
        fun networkTypeFor(wifiOnly: Boolean): NetworkType =
            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        private fun idTagOf(id: String): String = "$ID_TAG_PREFIX$id"

        /** Output data for a failed install, read back by [transferOf]. */
        fun failureData(outcome: PackInstallOutcome.Failed): Data =
            workDataOf(
                KEY_FAILURE_REASON to outcome.reason.name,
                KEY_NEEDED_BYTES to (outcome.neededBytes ?: NO_VALUE),
                KEY_FREE_BYTES to (outcome.freeBytes ?: NO_VALUE),
            )

        /**
         * Picks one transfer per pack from the work items. Several items can share a pack id (an
         * old failure and the retry after it), so a running item wins over a waiting one, and a
         * waiting one over a failure.
         */
        fun transfersOf(infos: List<WorkInfo>): Map<String, PackTransfer> =
            infos
                .mapNotNull { info ->
                    val id = packIdOf(info) ?: return@mapNotNull null
                    transferOf(info)?.let { transfer -> id to transfer }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, transfers) -> transfers.maxBy(::rankOf) }

        private fun packIdOf(info: WorkInfo): String? =
            info.tags.firstOrNull { it.startsWith(ID_TAG_PREFIX) }?.removePrefix(ID_TAG_PREFIX)

        /** Returns what the user sees for [info], or null once the install is finished. */
        fun transferOf(info: WorkInfo): PackTransfer? =
            when (info.state) {
                WorkInfo.State.RUNNING ->
                    PackTransfer.Running(info.progress.getFloat(KEY_PROGRESS, 0f))
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> PackTransfer.Waiting
                WorkInfo.State.FAILED -> failedTransferOf(info.outputData)
                WorkInfo.State.SUCCEEDED,
                WorkInfo.State.CANCELLED -> null
            }

        private fun failedTransferOf(output: Data): PackTransfer.Failed {
            val reason =
                output.getString(KEY_FAILURE_REASON)?.let { name ->
                    PackFailureReason.entries.firstOrNull { it.name == name }
                } ?: PackFailureReason.Other
            return PackTransfer.Failed(
                reason = reason,
                neededBytes = output.getLong(KEY_NEEDED_BYTES, NO_VALUE).takeIf { it != NO_VALUE },
                freeBytes = output.getLong(KEY_FREE_BYTES, NO_VALUE).takeIf { it != NO_VALUE },
            )
        }

        private fun rankOf(transfer: PackTransfer): Int =
            when (transfer) {
                is PackTransfer.Running -> 2
                PackTransfer.Waiting -> 1
                is PackTransfer.Failed -> 0
            }
    }
}
