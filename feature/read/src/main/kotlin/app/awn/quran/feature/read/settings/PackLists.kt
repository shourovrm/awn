package app.awn.quran.feature.read.settings

import androidx.annotation.StringRes
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.feature.read.R

/** The lists of downloadable entries that Settings opens from the Reading and Mushaf pages. */
enum class PackListKind(val group: CatalogueGroup, @StringRes val title: Int) {
    PageTypes(CatalogueGroup.PageType, R.string.pack_list_page_types),
    Translations(CatalogueGroup.Translation, R.string.pack_list_translations),
    Transliteration(CatalogueGroup.Transliteration, R.string.pack_list_transliteration),
    WordByWord(CatalogueGroup.WordByWord, R.string.pack_list_word_by_word),
}

/** The rows of one language in a list; [language] is null when the manifest does not say. */
internal data class LanguageGroup(val language: String?, val rows: List<CatalogueRow>)

/** Groups [rows] by language, in the order each language first appears in the manifest. */
internal fun groupByLanguage(rows: List<CatalogueRow>): List<LanguageGroup> =
    rows
        .groupBy { it.item.language }
        .map { (language, languageRows) -> LanguageGroup(language, languageRows) }

/** What the user is asked to confirm before a downloaded entry is deleted. */
internal data class PackRemoval(val packId: String, val name: String, val freedBytes: Long)

/**
 * Returns the confirmation to show for removing [row], or null when it cannot be removed: an entry
 * that is not on the phone, or one that ships inside the app.
 */
internal fun removalOf(row: CatalogueRow, name: String): PackRemoval? {
    if (!row.item.canRemove) return null
    return PackRemoval(
        packId = row.item.id,
        name = name,
        freedBytes = row.item.installedBytes ?: row.item.downloadBytes,
    )
}
