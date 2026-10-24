package app.awn.quran.core.data.search

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId

/** Which text of an ayah a search hit was found in. */
sealed interface SearchSource {

    data object Arabic : SearchSource

    data class Translation(val id: TranslationId) : SearchSource
}

/**
 * One ayah that matches. [text] is the text of [source] exactly as stored, and [matches] are the
 * character ranges of [text] to mark; they never overlap and are in reading order.
 */
data class SearchHit(
    val ayah: AyahRef,
    val source: SearchSource,
    val text: String,
    val matches: List<IntRange>,
)

/**
 * The ayahs that match [query], in Quran order; [count] is the number of ayahs. [isCloseMatch] is
 * true when nothing matched exactly and [hits] are the ayahs with words one letter off.
 */
data class SearchResults(
    val query: String,
    val hits: List<SearchHit>,
    val isCloseMatch: Boolean = false,
) {
    val count: Int
        get() = hits.size
}

interface SearchRepository {

    /**
     * Finds the ayahs whose Arabic text or installed translation contains [query] as a phrase.
     * Arabic ignores vowel signs and tatweel and treats every alef form as one letter; Latin text
     * ignores case and accents. A query shorter than two letters after that returns no hits. An
     * ayah that matches in several texts is returned once, with the Arabic text preferred. A query
     * of five letters or more that matches nothing is tried again allowing one letter of difference
     * per word; those hits come back with [SearchResults.isCloseMatch] set.
     */
    suspend fun search(query: String): SearchResults
}
