package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.ScriptId
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
    /** The print ticked in the card; the 1421 print until the user picks another. */
    val choice: ScriptId = ScriptId.V2,
    /** The print being downloaded from the card; null while the card asks. */
    val downloading: ScriptId? = null,
    /** The script the page showed before the card changed it, restored by Cancel. */
    val previousScript: ScriptId? = null,
    /** Counts failures that happened after the card was closed, so the screen says so once. */
    val closedFailureCount: Int = 0,
)
