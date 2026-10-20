package app.awn.quran.core.data.search

/**
 * The letters that only the Uthmani spelling has: the dagger alef, the small high yeh and the hamza
 * sign on a tatweel. [SearchNormaliser] drops them in the [DaggerAlefReading.Dropped] reading and
 * writes them out as keyboard letters in the other two.
 */
internal object UthmaniLetters {

    private const val DAGGER_ALEF = '\u0670'
    private const val HAMZA_ABOVE_MARK = '\u0654'
    private const val SMALL_HIGH_YEH = '\u06E7'
    private const val WAW = 'و'

    /**
     * Appends the keyboard spelling of the Uthmani-only letter at [index] of [text] and returns
     * true, or returns false when that character is not such a letter.
     */
    fun appendSpelledOut(
        text: String,
        index: Int,
        reading: DaggerAlefReading,
        into: StringBuilder,
    ): Boolean {
        when (text[index]) {
            DAGGER_ALEF -> appendDaggerAlef(text, index, reading, into)
            SMALL_HIGH_YEH -> into.append(SearchNormaliser.YA)
            HAMZA_ABOVE_MARK -> if (followsTatweel(text, index)) into.append(SearchNormaliser.ALEF)
            else -> return false
        }
        return true
    }

    private fun appendDaggerAlef(
        text: String,
        index: Int,
        reading: DaggerAlefReading,
        into: StringBuilder,
    ) {
        val carrierIndex = into.length - 1
        val carrier = into.lastOrNull()
        val carrierStartsWord =
            carrierIndex == 0 ||
                (carrierIndex > 0 && into[carrierIndex - 1] == SearchNormaliser.SPACE)
        when {
            // يَٰٓأَيُّهَا and يَٰقَوْمِ: the vocative "ya" is a word of its own in keyboard
            // spelling.
            carrier == SearchNormaliser.YA && carrierStartsWord -> {
                into.append(SearchNormaliser.ALEF)
                if (nextLetterContinuesWord(text, index)) into.append(SearchNormaliser.SPACE)
            }
            reading == DaggerAlefReading.ReplacingCarrier &&
                (carrier == WAW || carrier == SearchNormaliser.YA) ->
                into.setCharAt(carrierIndex, SearchNormaliser.ALEF)
            else -> into.append(SearchNormaliser.ALEF)
        }
    }

    /** True when a letter, not a space or the end, follows [index] after any marks. */
    private fun nextLetterContinuesWord(text: String, index: Int): Boolean {
        var next = index + 1
        while (next < text.length && SearchNormaliser.isIgnorableMark(text[next])) next += 1
        return next < text.length && !text[next].isWhitespace()
    }

    /** True when the hamza-above sign at [index] sits on a tatweel, as in يَسْـَٔلُونَكَ. */
    private fun followsTatweel(text: String, index: Int): Boolean {
        var previous = index - 1
        while (
            previous >= 0 &&
                text[previous] != SearchNormaliser.TATWEEL &&
                SearchNormaliser.isIgnorableMark(text[previous])
        ) {
            previous -= 1
        }
        return previous >= 0 && text[previous] == SearchNormaliser.TATWEEL
    }
}
