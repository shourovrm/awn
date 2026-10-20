package app.awn.quran.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.R
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.settings.CatalogueItemRow
import app.awn.quran.feature.read.settings.CatalogueViewModel
import java.util.Locale

/**
 * Everything the app can download, in sections: page types, translations by language, word by word
 * and transliteration, then a link to the reciters. Each item shows its size and offers download,
 * update or remove; the top row gives the space all downloads use. [onOpenReciters] opens the audio
 * downloads manager.
 */
@Composable
fun DownloadsPage(
    viewModel: CatalogueViewModel,
    onOpenReciters: () -> Unit,
    onBack: () -> Unit,
) {
    val catalogue by viewModel.state.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.settings_downloads), onBack = onBack) {
        ValueRow(
            label = stringResource(R.string.downloads_space_used),
            value = Formatter.formatShortFileSize(LocalContext.current, catalogue.spaceUsedBytes),
            onClick = null,
        )
        Section(R.string.downloads_page_types, catalogue.rowsIn(CatalogueGroup.PageType), viewModel)
        TranslationSection(catalogue.rowsIn(CatalogueGroup.Translation), viewModel)
        Section(
            R.string.downloads_word_by_word,
            catalogue.rowsIn(CatalogueGroup.WordByWord),
            viewModel,
        )
        Section(
            R.string.downloads_transliteration,
            catalogue.rowsIn(CatalogueGroup.Transliteration),
            viewModel,
        )
        SectionTitle(R.string.downloads_reciters)
        ValueRow(
            label = stringResource(R.string.downloads_reciters_open),
            value = "",
            onClick = onOpenReciters,
        )
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        text = stringResource(label),
        style = AwnTypography.lead,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.sectionGap, bottom = AwnSpacing.small),
    )
}

@Composable
private fun Section(title: Int, rows: List<CatalogueRow>, viewModel: CatalogueViewModel) {
    if (rows.isEmpty()) return
    SectionTitle(title)
    rows.forEach { row ->
        CatalogueItemRow(row = row, actions = viewModel.actionsFor(row.item.id))
    }
}

@Composable
private fun TranslationSection(rows: List<CatalogueRow>, viewModel: CatalogueViewModel) {
    if (rows.isEmpty()) return
    SectionTitle(R.string.downloads_translations)
    groupByLanguage(rows).forEach { group ->
        Text(
            text = languageName(group.language),
            style = AwnTypography.bodyStrong,
            color = Awn.field.buttonFill,
            modifier = Modifier.padding(top = AwnSpacing.small),
        )
        group.rows.forEach { row ->
            CatalogueItemRow(row = row, actions = viewModel.actionsFor(row.item.id))
        }
    }
}

@Composable
private fun languageName(code: String?): String {
    if (code == null) return stringResource(R.string.downloads_language_other)
    val appLocale = LocalConfiguration.current.locales[0]
    return Locale.forLanguageTag(code).getDisplayLanguage(appLocale).replaceFirstChar {
        it.titlecase(appLocale)
    }
}
