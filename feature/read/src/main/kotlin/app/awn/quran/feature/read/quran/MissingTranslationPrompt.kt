package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.catalogueItemName
import app.awn.quran.feature.read.settings.missingTranslations
import app.awn.quran.feature.read.settings.rememberCatalogueViewModel
import app.awn.quran.feature.read.settings.statusColour
import app.awn.quran.feature.read.settings.statusText

private const val PERCENT = 100

/**
 * One line shown over the Translation view while a translation the user turned on is not on the
 * phone: it names the translation and offers the download, then shows the progress. It shows
 * nothing when every enabled translation is there.
 */
@Composable
fun MissingTranslationPrompt(
    dependencies: ReadDependencies,
    enabled: List<TranslationId>,
    modifier: Modifier = Modifier,
) {
    val catalogueViewModel = rememberCatalogueViewModel(dependencies)
    val catalogue by catalogueViewModel.state.collectAsStateWithLifecycle()
    val row = missingTranslations(enabled, catalogue).firstOrNull() ?: return
    val field = Awn.field
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(field.background)
                .heightIn(min = AwnSpacing.touchTarget)
                .padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = promptText(row),
            style = AwnTypography.small,
            color = statusColour(row),
            modifier = Modifier.weight(1f),
        )
        if (!row.isBusy) {
            Text(
                text = stringResource(R.string.catalogue_download),
                style = AwnTypography.bodyStrong,
                color = field.buttonFill,
                modifier =
                    Modifier.heightIn(min = AwnSpacing.touchTarget)
                        .clickable(role = Role.Button) {
                            catalogueViewModel.install(row.item.id)
                        }
                        .padding(vertical = AwnSpacing.small),
            )
        }
    }
}

@Composable
private fun promptText(row: CatalogueRow): String {
    val name = catalogueItemName(row.item)
    return when (val transfer = row.transfer) {
        is PackTransfer.Running ->
            stringResource(
                R.string.translation_missing_progress,
                name,
                (transfer.progress * PERCENT).toInt(),
            )
        PackTransfer.Waiting -> stringResource(R.string.translation_missing_progress, name, 0)
        is PackTransfer.Failed -> statusText(row)
        null -> stringResource(R.string.translation_missing, name)
    }
}
