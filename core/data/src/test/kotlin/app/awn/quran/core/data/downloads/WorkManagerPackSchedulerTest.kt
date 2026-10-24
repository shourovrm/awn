package app.awn.quran.core.data.downloads

import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.workDataOf
import app.awn.quran.core.data.packs.PackFailureReason
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkManagerPackSchedulerTest {

    private fun info(
        packId: String,
        state: WorkInfo.State,
        progress: Data = Data.EMPTY,
        output: Data = Data.EMPTY,
    ) =
        WorkInfo(
            id = UUID.randomUUID(),
            state = state,
            tags = setOf(WorkManagerPackScheduler.WORK_TAG, "pack-id:$packId"),
            outputData = output,
            progress = progress,
        )

    @Test
    fun wifiOnlyMakesTheInstallWaitForAnUnmeteredNetwork() {
        assertEquals(NetworkType.UNMETERED, WorkManagerPackScheduler.networkTypeFor(true))
        assertEquals(NetworkType.CONNECTED, WorkManagerPackScheduler.networkTypeFor(false))
    }

    @Test
    fun queuedAndBlockedWorkIsWaiting() {
        assertEquals(
            PackTransfer.Waiting,
            WorkManagerPackScheduler.transferOf(info("v2", WorkInfo.State.ENQUEUED)),
        )
        assertEquals(
            PackTransfer.Waiting,
            WorkManagerPackScheduler.transferOf(info("v2", WorkInfo.State.BLOCKED)),
        )
    }

    @Test
    fun runningWorkReportsItsProgress() {
        val running =
            info(
                "v2",
                WorkInfo.State.RUNNING,
                progress = workDataOf(WorkManagerPackScheduler.KEY_PROGRESS to 0.4f),
            )

        assertEquals(PackTransfer.Running(0.4f), WorkManagerPackScheduler.transferOf(running))
    }

    @Test
    fun finishedWorkHasNoTransfer() {
        assertNull(WorkManagerPackScheduler.transferOf(info("v2", WorkInfo.State.SUCCEEDED)))
        assertNull(WorkManagerPackScheduler.transferOf(info("v2", WorkInfo.State.CANCELLED)))
    }

    @Test
    fun failedWorkCarriesTheReasonAndTheNumbers() {
        val outcome = PackInstallOutcome.Failed(PackFailureReason.NoSpace, 54_000_000L, 20_000_000L)
        val failed =
            info(
                "v2",
                WorkInfo.State.FAILED,
                output = WorkManagerPackScheduler.failureData(outcome),
            )

        assertEquals(
            PackTransfer.Failed(PackFailureReason.NoSpace, 54_000_000L, 20_000_000L),
            WorkManagerPackScheduler.transferOf(failed),
        )
    }

    @Test
    fun failureWithoutNumbersLeavesThemNull() {
        val failed =
            info(
                "v2",
                WorkInfo.State.FAILED,
                output =
                    WorkManagerPackScheduler.failureData(
                        PackInstallOutcome.Failed(PackFailureReason.ServerError),
                    ),
            )

        assertEquals(
            PackTransfer.Failed(PackFailureReason.ServerError, null, null),
            WorkManagerPackScheduler.transferOf(failed),
        )
    }

    @Test
    fun aRetryAfterAFailureShowsTheRetry() {
        val transfers =
            WorkManagerPackScheduler.transfersOf(
                listOf(
                    info("v2", WorkInfo.State.FAILED),
                    info("v2", WorkInfo.State.RUNNING),
                    info("v1", WorkInfo.State.SUCCEEDED),
                    info("en_abdul_haleem", WorkInfo.State.ENQUEUED),
                ),
            )

        assertEquals(
            mapOf(
                "v2" to PackTransfer.Running(0f),
                "en_abdul_haleem" to PackTransfer.Waiting,
            ),
            transfers,
        )
    }
}
