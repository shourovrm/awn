package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.downloads.CatalogueModel
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.feature.read.ReadDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What the word card offers while the chosen reciter's word audio is not ready, instead of the play
 * button: a download, its progress, or why it failed.
 */
sealed interface WordAudioOffer {
    /** The word timings are not on the phone; [downloadBytes] is the size of the download. */
    data class Download(val downloadBytes: Long) : WordAudioOffer

    /** The download is queued or running; [progress] runs from 0 to 1. */
    data class Downloading(val progress: Float) : WordAudioOffer

    /** The last download stopped; the card says why and offers the download again. */
    data class Failed(val failure: PackTransfer.Failed, val downloadBytes: Long) : WordAudioOffer
}

/** What the card can do with the sound of one word right now. */
internal sealed interface WordAudioState {
    /** The reciter's timings are on the phone: the card shows the play button. */
    data object Ready : WordAudioState

    /** The reciter has no word timings, or none for this ayah: the card shows nothing. */
    data object Unavailable : WordAudioState

    /** The reciter has word timings that are not ready yet. */
    data class Missing(val offer: WordAudioOffer) : WordAudioState
}

/**
 * Single words of the selected reciter. They exist only when the reciter has word timings, which
 * are a small download (about 3 MB) per reciter: the "Word audio" pack. This class finds out
 * whether the timings are on the phone, and starts and watches their download.
 */
internal class WordAudio(private val dependencies: ReadDependencies, scope: CoroutineScope) {
    private val catalogue = CatalogueModel(dependencies.packs, dependencies.downloads, scope)

    /**
     * The catalogue row of the selected reciter's word-timing pack, emitted again after each change
     * of reciter, install or transfer. It is null for a reciter with no word timings (Basfar's
     * timings mark ayahs only), and nothing is emitted before the catalogue is read.
     */
    val selectedPack: Flow<CatalogueRow?> =
        combine(dependencies.selectedReciterId.distinctUntilChanged(), catalogue.state) {
                reciterId,
                state ->
                reciterId to state
            }
            .filter { (_, state) -> state.isLoaded }
            .map { (reciterId, state) -> timingPackIdOf(reciterId)?.let { state.rowFor(it) } }
            .distinctUntilChanged()

    private suspend fun timingPackIdOf(reciterId: String): String? =
        dependencies.reciters
            ?.reciters()
            ?.firstOrNull { it.id == reciterId }
            ?.takeIf { it.wordByWord }
            ?.timingPack

    /** The state of [ayah]'s words for the card; [packRow] is the latest of [selectedPack]. */
    suspend fun stateOf(ayah: AyahRef, packRow: CatalogueRow?): WordAudioState {
        val reciterId = dependencies.selectedReciterId.first()
        if (dependencies.reciters?.ayahAudio(reciterId, ayah)?.words.orEmpty().isNotEmpty()) {
            return WordAudioState.Ready
        }
        if (packRow == null || packRow.item.isInstalled) return WordAudioState.Unavailable
        return WordAudioState.Missing(offerOf(packRow))
    }

    private fun offerOf(row: CatalogueRow): WordAudioOffer =
        when (val transfer = row.transfer) {
            PackTransfer.Waiting -> WordAudioOffer.Downloading(0f)
            is PackTransfer.Running -> WordAudioOffer.Downloading(transfer.progress)
            is PackTransfer.Failed -> WordAudioOffer.Failed(transfer, row.item.downloadBytes)
            null -> WordAudioOffer.Download(row.item.downloadBytes)
        }

    /** The latest row of the selected reciter's timing pack, waiting for the catalogue to load. */
    suspend fun currentPack(): CatalogueRow? = selectedPack.first()

    /** Downloads the timing pack [packId]; the space check and progress come from the catalogue. */
    fun download(packId: String) = catalogue.install(packId)

    suspend fun play(ayah: AyahRef, position: Int) {
        val reciterId = dependencies.selectedReciterId.first()
        dependencies.audioPlayer.playWord(reciterId, ayah, position)
    }
}

/**
 * True when the timing pack of [row] should download without being asked: it is not on the phone,
 * nothing is queued, the network is not metered and this screen has not asked for it before (so a
 * download the user cancelled or that failed is not started again behind their back).
 */
internal fun shouldDownloadWordAudio(
    row: CatalogueRow,
    isNetworkMetered: Boolean,
    alreadyRequestedIds: Set<String>,
): Boolean =
    !row.item.isInstalled &&
        row.transfer == null &&
        !isNetworkMetered &&
        row.item.id !in alreadyRequestedIds

/** True when [after] differs from [before] only in installed timing packs. */
internal fun changedTimingsOnly(before: List<InstalledPack>, after: List<InstalledPack>): Boolean =
    before != after && withoutTimings(before) == withoutTimings(after)

private fun withoutTimings(packs: List<InstalledPack>): List<InstalledPack> = packs.filterNot {
    it.kind == TIMING_KIND
}

private const val TIMING_KIND = "timing"
