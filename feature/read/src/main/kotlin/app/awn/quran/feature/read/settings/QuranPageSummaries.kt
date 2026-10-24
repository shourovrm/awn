package app.awn.quran.feature.read.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.feature.read.R

/** The names of the translations that are ticked, or "None" when no translation is shown. */
@Composable
internal fun translationsSummary(catalogue: CatalogueState, enabled: List<TranslationId>): String {
    val names =
        enabled.mapNotNull { id -> catalogue.rowFor(id.id) }.map { catalogueItemName(it.item) }
    return if (names.isEmpty()) stringResource(R.string.value_none) else names.joinToString(", ")
}

/** The names of the entries on the phone while the layer is shown, else "Off". */
@Composable
internal fun layerSummary(
    catalogue: CatalogueState,
    group: CatalogueGroup,
    isShown: Boolean,
): String {
    if (!isShown) return stringResource(R.string.value_off)
    val names =
        catalogue.rowsIn(group).filter { it.item.isInstalled }.map { catalogueItemName(it.item) }
    return if (names.isEmpty()) stringResource(R.string.value_none) else names.joinToString(", ")
}

@Composable
internal fun wordByWordRowSummary(catalogue: CatalogueState, settings: ReadSettings): String {
    if (!settings.showWordByWord) return stringResource(R.string.value_off)
    val appLocale = LocalConfiguration.current.locales[0]
    return wordByWordSummary(
        chosen = settings.wordByWordLanguage,
        appLanguage = appLocale.language,
        installedLanguages =
            installedWordLanguages(catalogue.rowsIn(CatalogueGroup.WordByWord)).toSet(),
        appLocale = appLocale,
    )
}
