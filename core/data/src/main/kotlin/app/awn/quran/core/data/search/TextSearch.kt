package app.awn.quran.core.data.search

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All the ayah texts of one [source], in the order they should be searched. */
class SearchableTexts(
    val source: SearchSource,
    val ayahs: List<AyahRef>,
    val texts: List<String>,
) {
    init {
        require(ayahs.size == texts.size) { "Each ayah needs exactly one text." }
    }
}

/**
 * Searches plain strings in memory. 6,236 ayahs in the Arabic and in up to four translations are
 * about 2 MB of text, so one `indexOf` per ayah is fast enough (see TextSearchPerformanceTest) and
 * no inverted index is built.
 *
 * [loadTexts] runs once, on the first search, and its result is kept for the life of the object.
 */
class TextSearch(
    private val loadTexts: suspend () -> List<SearchableTexts>,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SearchRepository {

    private class IndexedTexts(val texts: SearchableTexts, val normalised: List<String>)

    private val loadLock = Mutex()
    private var index: List<IndexedTexts>? = null

    override suspend fun search(query: String): SearchResults =
        withContext(computeDispatcher) {
            val normalisedQuery = SearchNormaliser.normalise(query)
            if (normalisedQuery.length < MINIMUM_QUERY_LENGTH) {
                return@withContext SearchResults(query, emptyList())
            }
            val hitsByAyah = LinkedHashMap<AyahRef, SearchHit>()
            for (indexed in loadIndex()) {
                collectHits(indexed, normalisedQuery, hitsByAyah)
            }
            SearchResults(query, hitsByAyah.values.sortedBy { it.ayah })
        }

    private suspend fun loadIndex(): List<IndexedTexts> = loadLock.withLock {
        index
            ?: loadTexts()
                .map { texts ->
                    IndexedTexts(texts, texts.texts.map(SearchNormaliser::normalise))
                }
                .also { index = it }
    }

    private fun collectHits(
        indexed: IndexedTexts,
        normalisedQuery: String,
        hitsByAyah: MutableMap<AyahRef, SearchHit>,
    ) {
        for (position in indexed.normalised.indices) {
            val ayah = indexed.texts.ayahs[position]
            // The first source to match owns the ayah, which prefers the Arabic text.
            val isNewMatch =
                ayah !in hitsByAyah && indexed.normalised[position].contains(normalisedQuery)
            if (isNewMatch) {
                val original = indexed.texts.texts[position]
                hitsByAyah[ayah] =
                    SearchHit(
                        ayah = ayah,
                        source = indexed.texts.source,
                        text = original,
                        matches = markedRanges(original, normalisedQuery),
                    )
            }
        }
    }

    /**
     * The ranges of [original] that hold [normalisedQuery], with the occurrences not overlapping.
     */
    private fun markedRanges(original: String, normalisedQuery: String): List<IntRange> {
        val positioned = SearchNormaliser.normaliseWithPositions(original)
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val start = positioned.text.indexOf(normalisedQuery, from)
            if (start < 0) break
            val end = start + normalisedQuery.length
            ranges += positioned.originalRange(original, start, end)
            from = end
        }
        return ranges
    }

    private companion object {
        const val MINIMUM_QUERY_LENGTH = 2
    }
}
