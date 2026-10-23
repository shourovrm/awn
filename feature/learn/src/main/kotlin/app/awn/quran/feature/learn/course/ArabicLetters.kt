package app.awn.quran.feature.learn.course

/**
 * Splits Arabic text into letters as a reader counts them: each letter with the vowel marks and
 * signs written on it. Lessons name the letters to colour by position, so the count must not depend
 * on how many marks a print writes.
 */
object ArabicLetters {

    /** The character range of each letter of [text], in reading order. */
    fun ranges(text: String): List<IntRange> {
        val starts = text.indices.filter { isLetter(text[it]) }
        return starts.mapIndexed { index, start ->
            val nextStart = starts.getOrNull(index + 1) ?: text.length
            start until markRunEnd(text, start + 1, nextStart)
        }
    }

    /** The ranges of the letters at the 1-based [positions]; positions past the end are ignored. */
    fun rangesAt(text: String, positions: List<Int>): List<IntRange> {
        val letters = ranges(text)
        return positions.mapNotNull { letters.getOrNull(it - 1) }
    }

    /** [text] without the pause sign that some words carry after a space. */
    fun withoutPauseSign(text: String): String = text.substringBefore(' ')

    // A letter's marks run until the next letter or the first character that is not Arabic.
    private fun markRunEnd(text: String, from: Int, limit: Int): Int {
        var end = from
        while (end < limit && isArabic(text[end])) end++
        return end
    }

    private fun isLetter(char: Char): Boolean = LETTER_RANGES.any { char.code in it }

    private fun isArabic(char: Char): Boolean = char.code in ARABIC_BLOCK

    private val ARABIC_BLOCK = 0x0600..0x06FF

    // The code points that start a letter; every other Arabic mark belongs to the letter before.
    private val LETTER_RANGES =
        listOf(
            0x0621..0x063A,
            0x0641..0x064A,
            0x0671..0x06D3,
            0x06D5..0x06D5,
            0x06EE..0x06EF,
            0x06FA..0x06FC,
        )
}
