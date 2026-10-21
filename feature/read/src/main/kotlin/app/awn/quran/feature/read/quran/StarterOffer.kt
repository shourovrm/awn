package app.awn.quran.feature.read.quran

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.catalogueItemName
import app.awn.quran.feature.read.settings.failureMessage
import app.awn.quran.feature.read.settings.rememberCatalogueViewModel
import app.awn.quran.feature.read.settings.rememberReadSettingsViewModel

private const val PERCENT = 100

/**
 * The two downloads that give a new reader something to read beside the Arabic: one translation and
 * the word-by-word meanings, both in the language the reader chose on the first screen.
 */
data class StarterPacks(val translationId: String, val wordByWordId: String) {
    val ids: List<String>
        get() = listOf(translationId, wordByWordId)
}

/** Returns the starter downloads for the app language [languageTag], or null for other tags. */
fun starterPacksFor(languageTag: String): StarterPacks? =
    when (languageTag) {
        "en" -> StarterPacks(translationId = "en_saheeh", wordByWordId = "wbw_en")
        "bn" -> StarterPacks(translationId = "bn_zakaria", wordByWordId = "wbw_bn")
        else -> null
    }

/** What the starter card shows, worked out from the catalogue rows of its downloads. */
internal data class StarterProgress(
    val rows: List<CatalogueRow>,
    /** The bytes still to download for the rows that are not on the phone. */
    val remainingBytes: Long,
    /** The bytes of those rows that have arrived already. */
    val partialBytes: Long,
    val isFinished: Boolean,
    val failure: PackTransfer.Failed?,
) {
    /**
     * Download progress from 0 to 1 of a run that began with [totalBytes] to fetch, weighted by the
     * size of each download.
     */
    fun fractionOf(totalBytes: Long): Float =
        if (totalBytes > 0) {
            ((totalBytes - remainingBytes + partialBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
}

internal fun starterProgressOf(rows: List<CatalogueRow>): StarterProgress {
    val missing = rows.filterNot { it.item.isInstalled }
    return StarterProgress(
        rows = rows,
        remainingBytes = missing.sumOf { it.item.downloadBytes },
        partialBytes =
            missing.sumOf { row ->
                val share = (row.transfer as? PackTransfer.Running)?.progress ?: 0f
                (row.item.downloadBytes * share).toLong()
            },
        isFinished = missing.isEmpty(),
        failure = rows.firstNotNullOfOrNull { it.transfer as? PackTransfer.Failed },
    )
}

/**
 * The one-time card on the first Quran screen that offers a translation and word-by-word meanings
 * for [languageTag]. Download ticks both for reading and starts the downloads, which go on in the
 * background; Not now and Keep reading close the card. [onHandled] runs once whenever the card is
 * done with, so the app never shows it again. A language without starter downloads, or downloads
 * already on the phone, closes it at once.
 */
@Composable
fun StarterOfferCard(
    dependencies: ReadDependencies,
    languageTag: String,
    onHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val starter = starterPacksFor(languageTag)
    val catalogueViewModel = rememberCatalogueViewModel(dependencies)
    val settingsViewModel = rememberReadSettingsViewModel(dependencies)
    val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()
    // The bytes to fetch when Download was pressed; 0 until then. The size shown while downloading
    // stays this figure, although it shrinks as each download finishes.
    var startedTotalBytes by rememberSaveable { mutableStateOf(0L) }

    val rows = starter?.ids?.mapNotNull { catalogue.rowFor(it) }.orEmpty()
    val progress = starterProgressOf(rows)
    val hasNothingToOffer =
        starter == null ||
            (catalogue.isLoaded && (rows.size < starter.ids.size || progress.isFinished))
    LaunchedEffect(hasNothingToOffer) { if (hasNothingToOffer) onHandled() }
    if (starter == null || !catalogue.isLoaded || hasNothingToOffer) return

    val translationRow = checkNotNull(catalogue.rowFor(starter.translationId))
    BackHandler(onBack = onHandled)
    FloatingCard(onDismiss = {}, modifier = modifier) {
        if (startedTotalBytes > 0 && progress.failure == null) {
            StarterDownloading(progress, startedTotalBytes, onKeepReading = onHandled) {
                starter.ids.forEach(catalogueViewModel::cancel)
                startedTotalBytes = 0L
            }
        } else {
            StarterAsking(
                translationName = catalogueItemName(translationRow.item),
                progress = progress,
                onDownload = {
                    settingsViewModel.setTranslationEnabled(
                        TranslationId(starter.translationId),
                        true,
                    )
                    settingsViewModel.setShowWordByWord(true)
                    progress.rows
                        .filterNot { it.item.isInstalled }
                        .forEach { catalogueViewModel.install(it.item.id) }
                    startedTotalBytes = progress.remainingBytes
                },
                onNotNow = onHandled,
            )
        }
    }
}

@Composable
private fun StarterAsking(
    translationName: String,
    progress: StarterProgress,
    onDownload: () -> Unit,
    onNotNow: () -> Unit,
) {
    val field = Awn.field
    Text(
        text = stringResource(R.string.starter_offer_title),
        style = AwnTypography.lead,
        color = field.content,
    )
    Text(
        text = stringResource(R.string.starter_offer_body, translationName),
        style = AwnTypography.small,
        color = field.mutedContent,
    )
    progress.failure?.let { failure ->
        Text(text = failureMessage(failure), style = AwnTypography.small, color = field.wrongWord)
    }
    Column(Modifier.padding(top = AwnSpacing.small)) {
        AwnButton(
            label =
                stringResource(
                    R.string.starter_offer_download_sized,
                    megabytesText(progress.remainingBytes),
                ),
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
        )
        CardTextButton(label = stringResource(R.string.starter_offer_not_now), onClick = onNotNow)
    }
}

@Composable
private fun StarterDownloading(
    progress: StarterProgress,
    totalBytes: Long,
    onKeepReading: () -> Unit,
    onCancel: () -> Unit,
) {
    val field = Awn.field
    Text(
        text = stringResource(R.string.starter_offer_downloading),
        style = AwnTypography.lead,
        color = field.content,
    )
    LinearProgressIndicator(
        progress = { progress.fractionOf(totalBytes) },
        modifier = Modifier.fillMaxWidth(),
        color = field.buttonFill,
        trackColor = field.softFill,
    )
    Text(
        text =
            stringResource(
                R.string.starter_offer_progress,
                (progress.fractionOf(totalBytes) * PERCENT).toInt(),
                megabytesText(totalBytes),
            ),
        style = AwnTypography.small,
        color = field.mutedContent,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        CardTextButton(
            label = stringResource(R.string.starter_offer_cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        AwnButton(
            label = stringResource(R.string.starter_offer_keep_reading),
            onClick = onKeepReading,
            modifier = Modifier.weight(1f),
        )
    }
}
