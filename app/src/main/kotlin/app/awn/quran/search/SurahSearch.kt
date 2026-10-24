package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import java.util.Locale

private const val MINIMUM_QUERY_LENGTH = 2

// Matches the article of a transliterated name ("Al-", "Ar-", "Ash-", ...) so a user may leave it
// out.
private val transliteratedArticle = Regex("^(adh|ash|ath|al|ad|an|ar|as|at|az)[\\s-]")

private const val ARABIC_ARTICLE = "ال"

/**
 * How well a surah matches: a name that starts with the query ranks before one that contains it.
 */
private enum class MatchRank {
    StartsWith,
    Contains,
}

/**
 * The surahs that [query] names, best match first. The query may be a surah number (in Latin,
 * Arabic-Indic or Bangla digits) or part of the transliterated, Arabic or spoken Bangla name or of
 * the Bangla meaning; hyphens, apostrophes, spaces, diacritics, the article ("Al-", "ال", "আল-")
 * and a leading "সূরা" do not matter, and Bangla spellings that sound alike are equal (see
 * [foldedWords]). A query shorter than 2 characters finds nothing unless it is a number.
 */
internal fun surahsMatching(query: String, chapters: List<Chapter>): List<Chapter> {
    val foldedQuery = withoutSuraWord(foldedWords(query.trim())).trim()
    val isNumber = foldedQuery.isNotEmpty() && foldedQuery.all { it.isDigit() }
    return if (isNumber) {
        chapters.filter { it.number == foldedQuery.toIntOrNull() }
    } else {
        surahsNamed(wantedSpellings(foldedQuery), chapters)
    }
}

/** The query as typed and without its Bangla article, because the article is optional. */
private fun wantedSpellings(foldedQuery: String): List<String> =
    listOf(foldedQuery, withoutBanglaArticle(foldedQuery))
        .map(::squeezed)
        .filter { it.length >= MINIMUM_QUERY_LENGTH }
        .distinct()

private fun surahsNamed(wantedSpellings: List<String>, chapters: List<Chapter>): List<Chapter> {
    val ranked = chapters.mapNotNull { chapter ->
        val nameForms = nameFormsOf(chapter)
        val bestRank = wantedSpellings.mapNotNull { rankOf(it, nameForms) }.minOrNull()
        bestRank?.let { rank -> rank to chapter }
    }
    return ranked.sortedBy { (rank, _) -> rank }.map { (_, chapter) -> chapter }
}

private fun rankOf(wanted: String, nameForms: List<String>): MatchRank? =
    when {
        nameForms.any { it.startsWith(wanted) } -> MatchRank.StartsWith
        nameForms.any { it.contains(wanted) } -> MatchRank.Contains
        else -> null
    }

/**
 * Every spelling of the chapter's names that a user may type, with and without the article. The
 * Bangla meaning is searched as well, so "গুহা" finds Al-Kahf.
 */
private fun nameFormsOf(chapter: Chapter): List<String> {
    val forms = mutableListOf<String>()

    val simpleName = chapter.nameSimple.lowercase(Locale.ROOT)
    forms += squeezed(foldedWords(simpleName))
    forms += squeezed(foldedWords(simpleName.replace(transliteratedArticle, "")))

    val arabicName = squeezed(foldedWords(chapter.nameArabic))
    forms += arabicName
    forms += arabicName.removePrefix(ARABIC_ARTICLE)

    val banglaName = foldedWords(chapter.nameBangla.orEmpty())
    forms += squeezed(banglaName)
    forms += squeezed(withoutBanglaArticle(banglaName))

    forms += squeezed(foldedWords(chapter.nameBanglaMeaning.orEmpty()))

    return forms.filter { it.isNotEmpty() }
}
