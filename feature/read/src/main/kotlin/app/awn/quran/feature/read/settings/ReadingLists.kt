package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow

private const val BANGLA_LANGUAGE = "bn"
private const val ENGLISH_LANGUAGE = "en"

/** The word-by-word meanings follow the app language, as on the Quran screen. */
internal fun wordLanguageOf(appLanguage: String): String =
    if (appLanguage == BANGLA_LANGUAGE) BANGLA_LANGUAGE else ENGLISH_LANGUAGE

/** Enabled translations in the user's order first, then the others in manifest order. */
internal fun orderedForDisplay(
    rows: List<CatalogueRow>,
    enabled: List<TranslationId>,
): List<CatalogueRow> {
    val enabledRows = enabled.mapNotNull { id -> rows.firstOrNull { it.item.id == id.id } }
    return enabledRows + rows.filterNot { it in enabledRows }
}
