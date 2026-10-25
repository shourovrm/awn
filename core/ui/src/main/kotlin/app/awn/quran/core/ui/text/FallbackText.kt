package app.awn.quran.core.ui.text

private const val RIGHT_TO_LEFT_MARK = 0x200F
private const val LEFT_TO_RIGHT_MARK = 0x200E

/**
 * Quran.com's text codes that the bundled QPC Hafs font draws as a dotted ring, mapped to the code
 * the font's own text (Quran.com `text_qpc_hafs`) uses for the same mark. Sukun and the
 * silent-letter circle trade places, so the map is applied one character at a time, never as two
 * replacements in a row. U+06E3 and U+06EB each occur once (52:37, 12:11).
 */
internal val HAFS_FONT_CODES =
    mapOf(
        '\u0652' to '\u06E1', // sukun
        '\u06DF' to '\u0652', // silent-letter circle
        '\u06E3' to '\u06DC', // small seen
        '\u06EB' to '\u06EC', // ishmam
    )

/**
 * True when [char] is a combining mark: it draws on the letter before it and, with no letter to sit
 * on, Android draws it on a dotted circle (U+25CC).
 */
fun isCombiningMark(char: Char): Boolean =
    when (Character.getType(char)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt() -> true
        else -> false
    }

/**
 * Prepares the Unicode text of a word for the Hafs fallback font. The Quran.com text puts a space
 * between a word and its pause mark ("رَيْبَ ۛ", 4,378 words), so the mark would start a new
 * cluster on the space and be drawn on a dotted circle; the space is removed and the mark sits on
 * the last letter again. Direction marks (U+200F, U+200E) are removed. The quarter sign U+06DE at
 * the start of 199 words is a symbol, not a mark, and stays: the Hafs font has a glyph for it.
 * Marks the font codes differently are swapped through [HAFS_FONT_CODES].
 */
fun normalizeFallbackText(text: String): String = normalizeFallbackTextWithMap(text).text

/**
 * [text] after [normalizeFallbackText]; [newIndexOf] has one entry per character of the original
 * text plus one for its end, and says where that character (or, for a removed one, the next kept
 * character) sits in [text]. A range of the original maps to the range between the entries of its
 * two ends.
 */
class FallbackTextMap(val text: String, private val newIndexOf: IntArray) {

    /** The range of [text] that holds the original characters of [original] (end inclusive). */
    fun mapRange(original: IntRange): IntRange {
        val start = newIndexOf[original.first].coerceAtMost(text.length)
        val endExclusive = newIndexOf[original.last + 1].coerceAtMost(text.length)
        return start until endExclusive
    }
}

/** [normalizeFallbackText] that also tells where each original character went. */
fun normalizeFallbackTextWithMap(text: String): FallbackTextMap {
    val builder = StringBuilder()
    val newIndexOf = IntArray(text.length + 1)
    for ((index, char) in text.withIndex()) {
        newIndexOf[index] = builder.length
        when {
            char.code == RIGHT_TO_LEFT_MARK || char.code == LEFT_TO_RIGHT_MARK -> Unit
            isCombiningMark(char) -> {
                while (builder.isNotEmpty() && builder.last().isWhitespace()) {
                    builder.setLength(builder.length - 1)
                }
                builder.append(HAFS_FONT_CODES[char] ?: char)
            }
            else -> builder.append(char)
        }
    }
    newIndexOf[text.length] = builder.length
    val leadingSpaces = builder.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
    val shifted = IntArray(newIndexOf.size) { index -> maxOf(0, newIndexOf[index] - leadingSpaces) }
    return FallbackTextMap(builder.toString().trim(), shifted)
}

/** True when the text would start with a combining mark and so draw it on a dotted circle. */
fun startsWithCombiningMark(text: String): Boolean =
    text.firstOrNull()?.let(::isCombiningMark) == true
