package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackTransfer
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

/**
 * The edition the card selects before the user chooses: Bangla readers start on the Indo-Pak print
 * they know, every other language on the print most Mushafs use today.
 */
internal fun preselectedPrintFor(languageTag: String): ScriptId =
    if (languageTag == "bn") ScriptId.IndoPak15 else ScriptId.V2

/** The state of the welcome card shown over the first Quran screen. */
data class PrintOfferState(
    val isOpen: Boolean = false,
    /** The edition selected in the card; exactly one is always selected. */
    val selectedScript: ScriptId = ScriptId.V2,
    /**
     * The translation row and the word-by-word row of the app language, in that order; empty when
     * the language has none. They stay in the state while their downloads run, so the progress can
     * be worked out.
     */
    val starterRows: List<CatalogueRow> = emptyList(),
    /**
     * Whether the translation and word-by-word tick box is ticked; ticked until the user unticks.
     */
    val isStarterTicked: Boolean = true,
    /** The editions being downloaded from the card; empty while the card asks. */
    val downloading: Set<ScriptId> = emptySet(),
    /** The catalogue ids of the translation downloads still running from the card. */
    val downloadingPackIds: Set<String> = emptySet(),
    /** The bytes everything started from the card had to fetch; the progress is weighted by it. */
    val startedBytes: Long = 0L,
    /** The script the page showed before the card changed it, restored by Cancel. */
    val previousScript: ScriptId? = null,
    /**
     * The print that becomes the page type once it is installed. Only an IndoPak print waits: its
     * pages cannot be read before its layouts arrive, unlike a Madani print, which shows the
     * bundled Hafs text meanwhile.
     */
    val scriptWhenInstalled: ScriptId? = null,
    /** Counts failures that happened after the card was closed, so the screen says so once. */
    val closedFailureCount: Int = 0,
) {
    /** True while a download started from the card is running. */
    val isDownloading: Boolean
        get() = downloading.isNotEmpty() || downloadingPackIds.isNotEmpty()

    /** True when the card shows the tick box: the language has downloads and one is missing. */
    val offersStarter: Boolean
        get() = starterRows.size == STARTER_ROW_COUNT && starterRows.any { !it.item.isInstalled }

    /** The translation downloads the Download button would fetch, in card order. */
    val tickedStarterRows: List<CatalogueRow>
        get() =
            if (offersStarter && isStarterTicked) {
                starterRows.filterNot { it.item.isInstalled }
            } else {
                emptyList()
            }
}

private const val STARTER_ROW_COUNT = 2

/**
 * The bytes the Download button fetches: the selected edition plus the ticked translation
 * downloads. An edition whose size is not known yet counts as 0.
 */
internal fun printOfferDownloadBytes(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
): Long {
    val editionBytes = statuses[offer.selectedScript]?.downloadBytes ?: 0L
    return editionBytes + offer.tickedStarterRows.sumOf { it.item.downloadBytes }
}

/**
 * Download progress from 0 to 1 over everything the card started, weighted by size: the bytes that
 * are no longer running count as done, the running ones count by their share.
 */
internal fun printOfferProgress(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
): Float {
    if (offer.startedBytes <= 0L) return 0f
    val runningEditionBytes = offer.downloading.sumOf { statuses[it]?.downloadBytes ?: 0L }
    val runningEditionDone =
        offer.downloading.sumOf { script ->
            val status = statuses[script]
            (status?.downloadBytes ?: 0L) * (status?.progress ?: 0f).toDouble()
        }
    val runningPackRows = offer.starterRows.filter { it.item.id in offer.downloadingPackIds }
    val runningPackBytes = runningPackRows.sumOf { it.item.downloadBytes }
    val runningPackDone = runningPackRows.sumOf { row ->
        val share = (row.transfer as? PackTransfer.Running)?.progress ?: 0f
        row.item.downloadBytes * share.toDouble()
    }
    val remainingBytes = runningEditionBytes + runningPackBytes
    val partialBytes = runningEditionDone + runningPackDone
    return ((offer.startedBytes - remainingBytes + partialBytes) / offer.startedBytes)
        .toFloat()
        .coerceIn(0f, 1f)
}

/** The first failure of what the card would download, shown above the buttons. */
internal fun printOfferFailure(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
): PackTransfer.Failed? =
    statuses[offer.selectedScript]?.failure
        ?: offer.tickedStarterRows.firstNotNullOfOrNull { it.transfer as? PackTransfer.Failed }

/**
 * Follows the downloads started from the card. An installed download leaves the running sets, a
 * failed one leaves them too and, when the card is closed, counts for the screen's note. The card
 * closes only when everything started is installed; a failure leaves it open on the question.
 */
internal fun settledPrintOffer(
    offer: PrintOfferState,
    statuses: Map<ScriptId, FontPackStatus>,
): PrintOfferState {
    if (!offer.isDownloading) return offer
    val installedEditions = offer.downloading.filter { statuses[it]?.isInstalled == true }.toSet()
    val failedEditions =
        offer.downloading.filter { statuses[it]?.failed == true }.toSet() - installedEditions
    val runningPackRows = offer.starterRows.filter { it.item.id in offer.downloadingPackIds }
    val installedPackIds = runningPackRows.filter { it.item.isInstalled }.map { it.item.id }.toSet()
    val failedPackIds =
        runningPackRows.filter { it.transfer is PackTransfer.Failed }.map { it.item.id }.toSet() -
            installedPackIds
    val runningEditions = offer.downloading - installedEditions - failedEditions
    val runningPackIds = offer.downloadingPackIds - installedPackIds - failedPackIds
    val failureCount = failedEditions.size + failedPackIds.size
    val settled =
        offer.copy(
            downloading = runningEditions,
            downloadingPackIds = runningPackIds,
            closedFailureCount = offer.closedFailureCount + if (offer.isOpen) 0 else failureCount,
        )
    val isEverythingInstalled =
        runningEditions.isEmpty() && runningPackIds.isEmpty() && failureCount == 0
    return if (isEverythingInstalled) settled.copy(isOpen = false) else settled
}
