package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import java.util.Locale

private const val MINIMUM_QUERY_LENGTH = 2

// Matches the article of a transliterated name ("Al-", "Ar-", "Ash-", ...) so a user may leave it
// out.
private val transliteratedArticle = Regex("^(adh|ash|ath|al|ad|an|ar|as|at|az)[\\s-]")

private const val ARABIC_ARTICLE = "ال"
private const val BANGLA_ARTICLE = "আল"
private const val ARABIC_TATWEEL = 'ـ'
private val arabicAlefVariants = setOf('أ', 'إ', 'آ', 'ٱ')

/**
 * How well a surah matches: a name that starts with the query ranks before one that contains it.
 */
private enum class MatchRank {
    StartsWith,
    Contains,
}

/**
 * The surahs that [query] names, best match first. The query may be a surah number or part of the
 * transliterated, Arabic or Bangla name; hyphens, apostrophes, spaces, diacritics and the article
 * ("Al-", "ال") do not matter. A query shorter than 2 characters finds nothing unless it is a
 * number.
 */
internal fun surahsMatching(query: String, chapters: List<Chapter>): List<Chapter> {
    val trimmedQuery = query.trim()
    val isNumber = trimmedQuery.isNotEmpty() && trimmedQuery.all { it.isDigit() }
    return if (isNumber) {
        chapters.filter { it.number == trimmedQuery.toIntOrNull() }
    } else {
        surahsNamed(lettersOnly(trimmedQuery), chapters)
    }
}

private fun surahsNamed(wanted: String, chapters: List<Chapter>): List<Chapter> {
    if (wanted.length < MINIMUM_QUERY_LENGTH) return emptyList()
    val ranked = chapters.mapNotNull { chapter ->
        rankOf(wanted, nameFormsOf(chapter))?.let { rank -> rank to chapter }
    }
    return ranked.sortedBy { (rank, _) -> rank }.map { (_, chapter) -> chapter }
}

private fun rankOf(wanted: String, nameForms: List<String>): MatchRank? =
    when {
        nameForms.any { it.startsWith(wanted) } -> MatchRank.StartsWith
        nameForms.any { it.contains(wanted) } -> MatchRank.Contains
        else -> null
    }

/** Every spelling of the chapter's names that a user may type, with and without the article. */
private fun nameFormsOf(chapter: Chapter): List<String> {
    val forms = mutableListOf<String>()

    val simpleName = chapter.nameSimple.lowercase(Locale.ROOT)
    forms += lettersOnly(simpleName)
    forms += lettersOnly(simpleName.replace(transliteratedArticle, ""))

    val arabicName = lettersOnly(chapter.nameArabic)
    forms += arabicName
    forms += arabicName.removePrefix(ARABIC_ARTICLE)

    val banglaName = lettersOnly(chapter.nameBangla.orEmpty())
    forms += banglaName
    forms += banglaName.removePrefix(BANGLA_ARTICLE)

    return forms.filter { it.isNotEmpty() }
}

/** Lower-case letters and digits only, with the Arabic alef variants and the tatweel unified. */
private fun lettersOnly(text: String): String =
    text
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() && it != ARABIC_TATWEEL }
        .map { if (it in arabicAlefVariants) 'ا' else it }
        .joinToString("")
