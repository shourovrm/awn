package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow

/** The language every other word-by-word pack falls back to. */
internal const val ENGLISH_WORD_LANGUAGE = "en"

/**
 * The language whose meanings the user gets: [chosen] when set, else [appLanguage], and English
 * when that language has no word-by-word pack on the phone ([installedLanguages]). The Word by word
 * list and the Settings row show this language as the selected one.
 */
internal fun wordLanguageShown(
    chosen: String?,
    appLanguage: String,
    installedLanguages: Set<String>,
): String {
    val wanted = chosen ?: appLanguage
    return if (wanted in installedLanguages) wanted else ENGLISH_WORD_LANGUAGE
}

/** The languages of the installed word-by-word packs, in manifest order. */
internal fun installedWordLanguages(rows: List<CatalogueRow>): List<String> =
    rows.filter { it.item.isInstalled }.mapNotNull { it.item.language }.distinct()

/** Enabled translations in the user's order first, then the others in manifest order. */
internal fun orderedForDisplay(
    rows: List<CatalogueRow>,
    enabled: List<TranslationId>,
): List<CatalogueRow> {
    val enabledRows = enabled.mapNotNull { id -> rows.firstOrNull { it.item.id == id.id } }
    return enabledRows + rows.filterNot { it in enabledRows }
}
