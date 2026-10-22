package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.Reciter
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

    /** No reciter has word timings, or none for this ayah: the card shows nothing. */
    data object Unavailable : WordAudioState

    /** The reciter has word timings that are not ready yet. */
    data class Missing(val offer: WordAudioOffer) : WordAudioState
}

/**
 * Single words of the word reciter (see [wordReciterFor]). Word timings are a small download (about
 * 3 MB) per reciter: the "Word audio" pack. This class finds out whether the timings are on the
 * phone, and starts and watches their download.
 */
internal class WordAudio(private val dependencies: ReadDependencies, scope: CoroutineScope) {
    private val catalogue = CatalogueModel(dependencies.packs, dependencies.downloads, scope)

    /**
     * The catalogue row of the word reciter's timing pack, emitted again after each change of
     * reciter, install or transfer. It is null when no reciter has word timings, and nothing is
     * emitted before the catalogue is read.
     */
    val selectedPack: Flow<CatalogueRow?> =
        combine(
                dependencies.selectedReciterId,
                dependencies.wordReciterId,
                catalogue.state,
            ) { listeningReciterId, chosenReciterId, state ->
                Triple(listeningReciterId, chosenReciterId, state)
            }
            .filter { (_, _, state) -> state.isLoaded }
            .map { (listeningReciterId, _, state) ->
                val wordReciter = wordReciterOf(listeningReciterId)
                wordReciter?.timingPack?.let { state.rowFor(it) }
            }
            .distinctUntilChanged()

    /**
     * The reciter whose voice plays single words: the one chosen in Settings > Audio when it has
     * word timings, else the listening reciter, else the first reciter with timings. Null when no
     * reciter has word timings.
     */
    private suspend fun wordReciterOf(listeningReciterId: String): Reciter? {
        val reciters = dependencies.reciters?.reciters().orEmpty()
        val wordReciters = reciters.filter { it.wordByWord && it.timingPack != null }
        val chosenId =
            wordReciterFor(
                chosen = dependencies.wordReciterId.first(),
                listening = listeningReciterId,
                withTimings = wordReciters.map { it.id },
            )
        return wordReciters.firstOrNull { it.id == chosenId }
    }

    /** The state of [ayah]'s words for the card; [packRow] is the latest of [selectedPack]. */
    suspend fun stateOf(ayah: AyahRef, packRow: CatalogueRow?): WordAudioState {
        val reciterId = wordReciterIdNow()
        val hasTimings =
            dependencies.reciters?.ayahAudio(reciterId, ayah)?.words.orEmpty().isNotEmpty()
        return when {
            hasTimings -> WordAudioState.Ready
            // No word reciter, or the timings are on the phone but hold nothing for this ayah.
            packRow == null || packRow.item.isInstalled -> WordAudioState.Unavailable
            else -> WordAudioState.Missing(offerOf(packRow))
        }
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
        val reciterId = wordReciterIdNow()
        dependencies.audioPlayer.playWord(reciterId, ayah, position)
    }

    // With no word reciter at all the listening id is kept, so the lookup finds no timings.
    private suspend fun wordReciterIdNow(): String {
        val selectedReciterId = dependencies.selectedReciterId.first()
        return wordReciterOf(selectedReciterId)?.id ?: selectedReciterId
    }
}

/**
 * The reciter whose voice plays a single word: [chosen] (Settings > Audio) when it has word
 * timings, else the [listening] reciter when it has them, else the first of [withTimings], else
 * null.
 */
internal fun wordReciterFor(
    chosen: String?,
    listening: String,
    withTimings: List<String>,
): String? =
    when {
        chosen != null && chosen in withTimings -> chosen
        listening in withTimings -> listening
        else -> withTimings.firstOrNull()
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
