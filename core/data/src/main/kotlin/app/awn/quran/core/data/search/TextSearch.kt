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

    /**
     * [normalisedReadings] holds, per text, its normalised forms: more than one only for Uthmani
     * Arabic.
     */
    private class IndexedTexts(
        val texts: SearchableTexts,
        val normalisedReadings: List<List<String>>,
    )

    private val loadLock = Mutex()
    private var index: List<IndexedTexts>? = null

    override suspend fun search(query: String): SearchResults =
        withContext(computeDispatcher) {
            val normalisedQueries = SearchNormaliser.normalisedQueries(query)
            if (normalisedQueries.first().length < MINIMUM_QUERY_LENGTH) {
                return@withContext SearchResults(query, emptyList())
            }
            val hitsByAyah = LinkedHashMap<AyahRef, SearchHit>()
            for (indexed in loadIndex()) {
                collectHits(indexed, normalisedQueries, hitsByAyah)
            }
            if (hitsByAyah.isEmpty()) {
                closeMatches(query, normalisedQueries)
            } else {
                SearchResults(query, hitsByAyah.values.sortedBy { it.ayah })
            }
        }

    /**
     * Runs only when the exact search found nothing, so a normal query pays nothing for it. It
     * compares word by word, which is slower than `contains`, and a short query would match too
     * much.
     */
    private suspend fun closeMatches(
        query: String,
        normalisedQueries: List<String>,
    ): SearchResults {
        val wordLists =
            normalisedQueries
                .filter { CloseMatches.letterCount(it) >= CloseMatches.MINIMUM_LETTERS }
                .map(CloseMatches::wordsOf)
        val hitsByAyah = LinkedHashMap<AyahRef, SearchHit>()
        if (wordLists.isNotEmpty()) {
            for (indexed in loadIndex()) {
                collectCloseHits(indexed, wordLists, hitsByAyah)
            }
        }
        val hits = hitsByAyah.values.sortedBy { it.ayah }
        return SearchResults(query, hits, isCloseMatch = hits.isNotEmpty())
    }

    private fun collectCloseHits(
        indexed: IndexedTexts,
        wordLists: List<List<String>>,
        hitsByAyah: MutableMap<AyahRef, SearchHit>,
    ) {
        for (position in indexed.normalisedReadings.indices) {
            val ayah = indexed.texts.ayahs[position]
            val isNewMatch =
                ayah !in hitsByAyah &&
                    indexed.normalisedReadings[position].any { reading ->
                        wordLists.any { CloseMatches.windows(reading, it).isNotEmpty() }
                    }
            if (isNewMatch) {
                val original = indexed.texts.texts[position]
                hitsByAyah[ayah] =
                    SearchHit(
                        ayah,
                        indexed.texts.source,
                        original,
                        closeRanges(original, wordLists),
                    )
            }
        }
    }

    /** Like [markedRanges], for the words that are one letter off. */
    private fun closeRanges(original: String, wordLists: List<List<String>>): List<IntRange> {
        for (reading in DaggerAlefReading.entries) {
            val positioned = SearchNormaliser.normaliseWithPositions(original, reading)
            for (words in wordLists) {
                val windows = CloseMatches.windows(positioned.text, words)
                if (windows.isNotEmpty()) {
                    return windows.map { positioned.originalRange(original, it.first, it.last + 1) }
                }
            }
        }
        return emptyList()
    }

    private suspend fun loadIndex(): List<IndexedTexts> = loadLock.withLock {
        index
            ?: loadTexts()
                .map { texts ->
                    IndexedTexts(texts, texts.texts.map(SearchNormaliser::normalisedReadings))
                }
                .also { index = it }
    }

    private fun collectHits(
        indexed: IndexedTexts,
        normalisedQueries: List<String>,
        hitsByAyah: MutableMap<AyahRef, SearchHit>,
    ) {
        for (position in indexed.normalisedReadings.indices) {
            val ayah = indexed.texts.ayahs[position]
            // The first source to match owns the ayah, which prefers the Arabic text.
            val isNewMatch =
                ayah !in hitsByAyah &&
                    indexed.normalisedReadings[position].any { reading ->
                        normalisedQueries.any { reading.contains(it) }
                    }
            if (isNewMatch) {
                val original = indexed.texts.texts[position]
                hitsByAyah[ayah] =
                    SearchHit(
                        ayah = ayah,
                        source = indexed.texts.source,
                        text = original,
                        matches = markedRanges(original, normalisedQueries),
                    )
            }
        }
    }

    /**
     * The ranges of [original] that hold one of [normalisedQueries], with the occurrences not
     * overlapping, found in the first reading of the dagger alef and the first query that match.
     */
    private fun markedRanges(original: String, normalisedQueries: List<String>): List<IntRange> {
        for (reading in DaggerAlefReading.entries) {
            val positioned = SearchNormaliser.normaliseWithPositions(original, reading)
            val query = normalisedQueries.firstOrNull { positioned.text.contains(it) } ?: continue
            return rangesOf(original, positioned, query)
        }
        return emptyList()
    }

    private fun rangesOf(
        original: String,
        positioned: NormalisedText,
        normalisedQuery: String,
    ): List<IntRange> {
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
