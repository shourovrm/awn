package app.awn.quran.settings

import app.awn.quran.core.data.downloads.CatalogueRow

/** The rows of one language in the translations section; [language] is null when not stated. */
internal data class LanguageGroup(val language: String?, val rows: List<CatalogueRow>)

/** Groups [rows] by language, in the order each language first appears in the manifest. */
internal fun groupByLanguage(rows: List<CatalogueRow>): List<LanguageGroup> =
    rows
        .groupBy { it.item.language }
        .map { (language, languageRows) ->
            LanguageGroup(language, languageRows)
        }
