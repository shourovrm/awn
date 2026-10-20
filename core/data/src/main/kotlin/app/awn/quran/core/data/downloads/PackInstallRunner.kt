package app.awn.quran.core.data.downloads

import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.data.packs.PackInstallException
import java.io.IOException

/** How one background install attempt ended. */
sealed interface PackInstallOutcome {
    data object Succeeded : PackInstallOutcome

    /** Interrupted by the connection; try again when the network is back. */
    data object RetryLater : PackInstallOutcome

    /** A retry cannot fix this; the user has to act. */
    data class Failed(
        val reason: PackFailureReason,
        val neededBytes: Long? = null,
        val freeBytes: Long? = null,
    ) : PackInstallOutcome
}

/**
 * Runs the install of one pack and keeps the wanted list true: the id leaves it when the install
 * succeeds or fails for good, and stays while the attempt may be repeated. A cancelled attempt
 * leaves the list alone, because the system stopping the work is not the user giving up.
 */
class PackInstallRunner(
    private val packs: PackRepository,
    private val wanted: WantedPackStore,
) {

    suspend fun run(packId: String, onProgress: (Float) -> Unit): PackInstallOutcome {
        val item = packs.catalogue().firstOrNull { it.id == packId }
        if (item == null) {
            wanted.remove(packId)
            return PackInstallOutcome.Failed(PackFailureReason.Other)
        }
        return try {
            packs.install(item.entry, onProgress)
            wanted.remove(packId)
            PackInstallOutcome.Succeeded
        } catch (error: PackInstallException) {
            outcomeOf(packId, error.reason, error.neededBytes, error.freeBytes)
        } catch (error: IOException) {
            outcomeOf(packId, PackFailureReason.of(error), null, null)
        }
    }

    private fun outcomeOf(
        packId: String,
        reason: PackFailureReason,
        neededBytes: Long?,
        freeBytes: Long?,
    ): PackInstallOutcome {
        if (reason == PackFailureReason.NoInternet) return PackInstallOutcome.RetryLater
        wanted.remove(packId)
        return PackInstallOutcome.Failed(reason, neededBytes, freeBytes)
    }
}
