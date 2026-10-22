package app.awn.quran.feature.learn.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.feature.learn.R

/** What a unit says about the recorded sounds it plays. */
internal sealed interface SoundsOffer {
    /** The sounds are on the phone, or no sounds are listed: nothing to show. */
    data object None : SoundsOffer

    data class Download(val bytes: Long) : SoundsOffer

    data object Downloading : SoundsOffer
}

/** The offer and the action behind its button. */
internal class SoundsOfferState(val offer: SoundsOffer, val onDownload: () -> Unit) {
    companion object {
        val None = SoundsOfferState(SoundsOffer.None) {}
    }
}

/**
 * The offer for the catalogue row of the Learn sounds, or [SoundsOffer.None] when the row is
 * missing or installed. A queued or running transfer is shown as downloading; a failed one offers
 * the download again.
 */
internal fun soundsOfferOf(item: CatalogueItem?, transfer: PackTransfer?): SoundsOffer =
    when {
        item == null || item.isInstalled -> SoundsOffer.None
        transfer is PackTransfer.Waiting || transfer is PackTransfer.Running ->
            SoundsOffer.Downloading
        else -> SoundsOffer.Download(item.downloadBytes)
    }

@Composable
internal fun rememberSoundsOffer(dependencies: CourseDependencies): SoundsOfferState {
    val installed by
        remember(dependencies.packs) { dependencies.packs.installed() }.collectAsState(emptyList())
    val item by
        produceState<CatalogueItem?>(null, installed) {
            value =
                dependencies.packs.catalogue().firstOrNull { it.group == CatalogueGroup.LearnAudio }
        }
    val transfers by dependencies.downloads.transfers.collectAsState(emptyMap())
    val row = item
    return SoundsOfferState(soundsOfferOf(row, row?.let { transfers[it.id] })) {
        row?.let { dependencies.downloads.request(it.id) }
    }
}

/** The download button, or the line that says the download runs. */
@Composable
internal fun SoundsOfferRow(state: SoundsOfferState, modifier: Modifier = Modifier) {
    when (val offer = state.offer) {
        SoundsOffer.None -> Unit
        is SoundsOffer.Download ->
            AwnButton(
                label =
                    stringResource(
                        R.string.unit_sounds_download,
                        Formatter.formatShortFileSize(LocalContext.current, offer.bytes),
                    ),
                onClick = state.onDownload,
                modifier = modifier.fillMaxWidth(),
            )
        SoundsOffer.Downloading ->
            MutedLine(stringResource(R.string.unit_sounds_downloading), modifier)
    }
}
