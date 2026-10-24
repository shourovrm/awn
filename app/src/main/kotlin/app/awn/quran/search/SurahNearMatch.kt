package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.search.editDistance

private const val SHORTEST_QUERY_WITH_TYPO = 4
private const val LONGEST_QUERY_WITH_ONE_TYPO = 6
private const val ONE_TYPO = 1
private const val TWO_TYPOS = 2

/** How many letters of difference a query of [letterCount] letters forgives. */
private fun allowedDifference(letterCount: Int): Int =
    when {
        letterCount < SHORTEST_QUERY_WITH_TYPO -> 0
        letterCount <= LONGEST_QUERY_WITH_ONE_TYPO -> ONE_TYPO
        else -> TWO_TYPOS
    }

/**
 * The surahs with a name within the allowed edit distance of a [wantedSpellings] entry, nearest
 * first. It is the last resort for a mistyped name ("iklas" for Al-Ikhlas), tried only when no name
 * starts with or contains the query. A name is compared with the query by its first letters, so a
 * half-typed name with a typo still finds the surah. Only Latin queries are forgiven, because the
 * Bangla and Arabic spellings are already folded by sound.
 */
internal fun surahsNearlyNamed(
    wantedSpellings: List<String>,
    chapters: List<Chapter>,
    formsOfName: (Chapter) -> List<String>,
): List<Chapter> {
    val latinSpellings = wantedSpellings.filter(::isLatinWord)
    val nearest = chapters.mapNotNull { chapter ->
        val forms = formsOfName(chapter)
        val distance = latinSpellings.mapNotNull { nearestPrefixDistance(it, forms) }.minOrNull()
        distance?.let { it to chapter }
    }
    return nearest.sortedBy { (distance, _) -> distance }.map { (_, chapter) -> chapter }
}

/** The smallest distance from [wanted] to the start of any of [nameForms], or null if too far. */
private fun nearestPrefixDistance(wanted: String, nameForms: List<String>): Int? {
    val limit = allowedDifference(wanted.length)
    if (limit == 0) return null
    val distances = nameForms.flatMap { form ->
        val shortest = maxOf(1, wanted.length - limit)
        val longest = minOf(form.length, wanted.length + limit)
        (shortest..longest).map { length -> editDistance(wanted, form.take(length), limit) }
    }
    return distances.filter { it <= limit }.minOrNull()
}
