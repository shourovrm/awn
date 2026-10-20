package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.search.SearchHit
import app.awn.quran.core.data.search.SearchRepository
import app.awn.quran.core.data.search.SearchResults

/** Returns the [hits] it was given for every query, so tests choose what a search finds. */
class FakeSearchRepository(var hits: List<SearchHit> = emptyList()) : SearchRepository {

    /** The queries received, oldest first. */
    val queries = mutableListOf<String>()

    override suspend fun search(query: String): SearchResults {
        queries += query
        return SearchResults(query, hits)
    }
}
