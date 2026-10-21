package app.awn.quran.settings

import java.text.Normalizer
import java.util.Locale

/**
 * One setting, or a whole settings page when [rowKey] is null, that the search field can find.
 * [searchTexts] holds the label and the names of the values, in English and in Bangla, so that a
 * Bangla query finds a setting while the app is in English.
 */
class SettingsSearchEntry(
    val route: String,
    val rowKey: String?,
    val label: String,
    val pageLabel: String,
    val searchTexts: List<String>,
)

/** Lowercases and composes [text] so that the same word typed two ways compares equal. */
internal fun normaliseForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

/**
 * Returns the entries that match [query], best first. Every word of the query must appear somewhere
 * in the entry's texts. Entries whose own label starts with the query come first, then entries
 * whose label contains it, then entries matched only by a value name or the other language. A blank
 * query matches nothing.
 */
fun searchSettings(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
    val normalisedQuery = normaliseForSearch(query)
    if (normalisedQuery.isEmpty()) return emptyList()
    val words = normalisedQuery.split(WHITESPACE)
    return entries
        .filter { entry ->
            val haystack = entry.searchTexts.joinToString(" ") { normaliseForSearch(it) }
            words.all { word -> word in haystack }
        }
        .sortedBy { entry ->
            val label = normaliseForSearch(entry.label)
            when {
                label.startsWith(normalisedQuery) -> 0
                normalisedQuery in label -> 1
                else -> 2
            }
        }
}

private val WHITESPACE = Regex("\\s+")
