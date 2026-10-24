package app.awn.quran.core.data.search

/**
 * Finds a phrase whose words are each at most one letter away from the words of a text. This is the
 * fallback for a query that has no exact hit, such as a mistyped word.
 */
internal object CloseMatches {

    /** A word shorter than this must match exactly, or every two-letter word would be "close". */
    private const val SHORTEST_WORD_THAT_MAY_DIFFER = 3
    private const val ALLOWED_DIFFERENCE = 1
    private const val SPACE = SearchNormaliser.SPACE

    /** The minimum number of letters in a query before a close match is tried. */
    const val MINIMUM_LETTERS = 5

    fun letterCount(normalisedQuery: String): Int = normalisedQuery.count { it != SPACE }

    fun wordsOf(normalisedQuery: String): List<String> =
        normalisedQuery.split(SPACE).filter { it.isNotEmpty() }

    /**
     * The ranges of [normalisedText] that hold [queryWords] as consecutive words, each word at most
     * one letter away from its counterpart. Matches do not overlap.
     */
    fun windows(normalisedText: String, queryWords: List<String>): List<IntRange> {
        val words = wordRanges(normalisedText)
        val found = mutableListOf<IntRange>()
        var first = 0
        while (first + queryWords.size <= words.size) {
            val isMatch =
                queryWords.indices.all { offset ->
                    wordsAreClose(
                        queryWords[offset],
                        normalisedText.substring(words[first + offset]),
                    )
                }
            if (isMatch) {
                found += words[first].first..words[first + queryWords.size - 1].last
                first += queryWords.size
            } else {
                first += 1
            }
        }
        return found
    }

    /**
     * The range of every word of [normalisedText], without the punctuation around it: "mercy." is
     * compared as "mercy", because a typed word has no full stop.
     */
    private fun wordRanges(normalisedText: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var index = 0
        while (index < normalisedText.length) {
            val spaceIndex = normalisedText.indexOf(SPACE, index)
            val end = if (spaceIndex < 0) normalisedText.length else spaceIndex
            val trimmed = withoutSurroundingPunctuation(normalisedText, index, end)
            if (!trimmed.isEmpty()) ranges += trimmed
            index = end + 1
        }
        return ranges
    }

    private fun withoutSurroundingPunctuation(
        text: String,
        start: Int,
        endExclusive: Int,
    ): IntRange {
        var first = start
        var last = endExclusive - 1
        while (first <= last && !isWordCharacter(text[first])) first += 1
        while (last >= first && !isWordCharacter(text[last])) last -= 1
        return first..last
    }

    /** Letters, digits and the vowel signs and other marks that Bangla writes as part of a word. */
    private fun isWordCharacter(character: Char): Boolean {
        if (character.isLetterOrDigit()) return true
        val type = Character.getType(character)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun wordsAreClose(queryWord: String, textWord: String): Boolean =
        when {
            queryWord == textWord -> true
            queryWord.length < SHORTEST_WORD_THAT_MAY_DIFFER -> false
            else -> editDistance(queryWord, textWord, ALLOWED_DIFFERENCE) <= ALLOWED_DIFFERENCE
        }
}
