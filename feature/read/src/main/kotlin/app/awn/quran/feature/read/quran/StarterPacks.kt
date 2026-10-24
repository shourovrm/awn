package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.CatalogueState

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

/**
 * The translation row followed by the word-by-word row for [languageTag], or an empty list when the
 * language has no starter downloads or the catalogue lacks either of them.
 */
internal fun starterRowsFor(languageTag: String, catalogue: CatalogueState): List<CatalogueRow> {
    val starter = starterPacksFor(languageTag) ?: return emptyList()
    val rows = starter.ids.mapNotNull { catalogue.rowFor(it) }
    return if (rows.size == starter.ids.size) rows else emptyList()
}
