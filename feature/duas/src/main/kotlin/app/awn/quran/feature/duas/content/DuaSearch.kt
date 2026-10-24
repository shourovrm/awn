package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.search.SearchNormaliser
import app.awn.quran.core.data.search.editDistance

/** The texts one dua can be found by. [translation] is null when no translation is installed. */
data class DuaSearchEntry(
    val dua: Dua,
    val arabic: String,
    val translation: String?,
    val groupName: String,
)

/** Which text matched, so the result row can show that text. */
enum class DuaMatchField {
    Arabic,
    Translation,
    Group,
}

data class DuaSearchHit(val dua: Dua, val field: DuaMatchField)

/**
 * Finds duas by Arabic without harakat, translation words, or group name. Every query word must
 * match a word of the same field; the first field in the order Arabic, Translation, Group that
 * matches is the one reported.
 */
object DuaSearch {
    /** Query words of this many letters or more may also match a field word one letter away. */
    private const val MIN_LETTERS_FOR_TYPO = 5
    private const val ALLOWED_TYPO_DISTANCE = 1

    /** Anything that is not a letter, a combining mark or a digit separates two words. */
    private val wordSeparator = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun search(query: String, entries: List<DuaSearchEntry>): List<DuaSearchHit> {
        if (query.isBlank()) return emptyList()
        val queryForms = SearchNormaliser.normalisedQueries(query).map(::splitIntoWords)
        return entries.mapNotNull { entry ->
            val field = firstMatchingField(entry, queryForms)
            if (field == null) null else DuaSearchHit(entry.dua, field)
        }
    }

    private fun firstMatchingField(
        entry: DuaSearchEntry,
        queryForms: List<List<String>>,
    ): DuaMatchField? =
        DuaMatchField.entries.firstOrNull { field ->
            val text = textOf(entry, field)
            val fieldWords = if (text == null) emptyList() else splitIntoWords(text)
            queryForms.any { queryWords -> allWordsMatch(queryWords, fieldWords) }
        }

    private fun textOf(entry: DuaSearchEntry, field: DuaMatchField): String? =
        when (field) {
            DuaMatchField.Arabic -> entry.arabic
            DuaMatchField.Translation -> entry.translation
            DuaMatchField.Group -> entry.groupName
        }

    /** Folds [text] with the shared normaliser, then cuts it at spaces and punctuation. */
    private fun splitIntoWords(text: String): List<String> =
        SearchNormaliser.normalise(text).split(wordSeparator).filter { it.isNotEmpty() }

    private fun allWordsMatch(queryWords: List<String>, fieldWords: List<String>): Boolean =
        queryWords.isNotEmpty() &&
            queryWords.all { queryWord ->
                fieldWords.any { fieldWord -> wordMatches(queryWord, fieldWord) }
            }

    private fun wordMatches(queryWord: String, fieldWord: String): Boolean {
        val startsWithQuery = fieldWord.startsWith(queryWord)
        val mayHaveTypo = queryWord.length >= MIN_LETTERS_FOR_TYPO
        return startsWithQuery ||
            (mayHaveTypo &&
                editDistance(queryWord, fieldWord, ALLOWED_TYPO_DISTANCE) <= ALLOWED_TYPO_DISTANCE)
    }
}
