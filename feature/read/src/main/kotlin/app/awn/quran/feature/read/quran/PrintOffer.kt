package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.page.FontPackStatus
import app.awn.quran.feature.read.page.pageTypeScripts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Remembers whether the one-time offer of a printed Mushaf is still to be shown. The app marks it
 * pending when a new installation answers the language screen, so an installation that existed
 * before the offer was built never has it pending.
 */
interface PrintOfferStore {

    /** True from the first-launch language answer until the user handles the offer. */
    val isPending: Flow<Boolean>

    /** Records that the user downloaded, said Not now or closed the card; it never returns. */
    suspend fun markHandled()

    /** A store that never offers anything, for screens opened without a first launch. */
    object Never : PrintOfferStore {
        override val isPending: Flow<Boolean> = flowOf(false)

        override suspend fun markHandled() = Unit
    }
}

/**
 * The card opens when the offer is pending and no page-font print is installed yet; a phone that
 * already has one (an upgrade, or a restore) is never asked.
 */
internal fun shouldOpenPrintOffer(isPending: Boolean, installedPackIds: Set<String>): Boolean =
    isPending && pageTypeScripts.none { it.packId in installedPackIds }

/** The state of the print offer card shown over the first Quran screen. */
data class PrintOfferState(
    val isOpen: Boolean = false,
    /** The prints ticked in the card; the 1421 print until the user ticks or unticks another. */
    val choices: Set<ScriptId> = setOf(ScriptId.V2),
    /** The prints being downloaded from the card; empty while the card asks. */
    val downloading: Set<ScriptId> = emptySet(),
    /** The script the page showed before the card changed it, restored by Cancel. */
    val previousScript: ScriptId? = null,
    /** Counts failures that happened after the card was closed, so the screen says so once. */
    val closedFailureCount: Int = 0,
)

/**
 * The bytes the ticked prints download, shown on the Download button. A print whose size is not
 * known yet counts as 0, so the sum is 0 until the catalogue reports a size.
 */
internal fun printOfferDownloadBytes(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
): Long = offer.choices.sumOf { statuses[it]?.downloadBytes ?: 0L }
