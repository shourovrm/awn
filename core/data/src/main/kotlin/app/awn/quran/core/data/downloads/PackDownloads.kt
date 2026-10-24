package app.awn.quran.core.data.downloads

import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.packs.PackFailureReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** What the user sees for a download that is queued, running or failed. Finished ones have none. */
sealed interface PackTransfer {

    /** Queued, or waiting for a connection. */
    data object Waiting : PackTransfer

    /** Downloading; [progress] runs from 0 to 1. */
    data class Running(val progress: Float) : PackTransfer

    /**
     * Stopped for [reason]. [neededBytes] and [freeBytes] are set for [PackFailureReason.NoSpace]
     * so the message can give the numbers.
     */
    data class Failed(
        val reason: PackFailureReason,
        val neededBytes: Long? = null,
        val freeBytes: Long? = null,
    ) : PackTransfer
}

/** Remembers which packs the user asked for and has not received yet, across process death. */
interface WantedPackStore {
    fun ids(): Set<String>

    fun add(id: String)

    fun remove(id: String)
}

/** Runs the installs in the background, outside the screens that ask for them. */
interface PackWorkScheduler {

    /** The current transfer of each pack id that has one. */
    val transfers: Flow<Map<String, PackTransfer>>

    /** Starts the install of [id]; does nothing when one is already queued or running. */
    fun enqueue(id: String)

    /** Stops the install of [id] and forgets it. */
    fun cancel(id: String)

    /** Forgets finished and failed installs, so an old failure does not show after a restart. */
    fun pruneFinished()
}

/**
 * The entry point for downloads. A request is written to [wanted] before it is scheduled, so an
 * install that the system stops, or that dies with the process, starts again on the next launch
 * ([resumeWanted]) until it succeeds, fails for a reason a retry cannot fix, or the user cancels.
 */
class PackDownloads(
    private val wanted: WantedPackStore,
    private val scheduler: PackWorkScheduler,
    private val networkRule: DownloadNetworkRule = DownloadNetworkRule.Unrestricted,
) {
    /** Requests refused because the phone was on mobile data while downloads are Wi-Fi only. */
    private val refusedForWifi = MutableStateFlow<Set<String>>(emptySet())

    /** The scheduler's transfers, plus a failure the user can retry for each refused request. */
    val transfers: Flow<Map<String, PackTransfer>> =
        combine(scheduler.transfers, refusedForWifi) { scheduled, refused ->
            val refusals = refused.associateWith { PackTransfer.Failed(PackFailureReason.WifiOnly) }
            refusals + scheduled
        }

    /**
     * Starts installing, or updating, pack [id]. On mobile data while downloads are Wi-Fi only the
     * request is not scheduled: it shows as a failure the user can retry, and the wanted flag makes
     * it start on the next launch, where the scheduler waits for Wi-Fi.
     */
    fun request(id: String) {
        wanted.add(id)
        if (networkRule.mayDownload()) {
            refusedForWifi.update { it - id }
            scheduler.enqueue(id)
        } else {
            refusedForWifi.update { it + id }
        }
    }

    /** Stops installing pack [id] and does not restart it on the next launch. */
    fun cancel(id: String) {
        wanted.remove(id)
        refusedForWifi.update { it - id }
        scheduler.cancel(id)
    }

    /**
     * Restarts every wanted install that is not finished. A wanted id that the manifests no longer
     * list, or that is installed and current, is dropped from the wanted list.
     */
    suspend fun resumeWanted(packs: PackRepository) {
        scheduler.pruneFinished()
        val wantedIds = wanted.ids()
        if (wantedIds.isEmpty()) return
        val itemsById = packs.catalogue().associateBy { it.id }
        for (id in wantedIds) {
            val item = itemsById[id]
            if (item == null || (item.isInstalled && !item.hasUpdate)) {
                wanted.remove(id)
            } else {
                refusedForWifi.update { it - id }
                scheduler.enqueue(id)
            }
        }
    }
}
