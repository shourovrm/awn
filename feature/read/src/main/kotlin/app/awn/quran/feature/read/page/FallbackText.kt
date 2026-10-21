package app.awn.quran.feature.read.page

private const val RIGHT_TO_LEFT_MARK = 0x200F
private const val LEFT_TO_RIGHT_MARK = 0x200E

/**
 * Quran.com's text codes that the bundled QPC Hafs font draws as a dotted ring, mapped to the code
 * the font's own text (Quran.com `text_qpc_hafs`) uses for the same mark. Sukun and the
 * silent-letter circle trade places, so the map is applied one character at a time, never as two
 * replacements in a row. U+06E3 and U+06EB each occur once (52:37, 12:11).
 */
private val HAFS_FONT_CODES =
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
internal fun isCombiningMark(char: Char): Boolean =
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
internal fun normalizeFallbackText(text: String): String {
    val builder = StringBuilder()
    for (char in text) {
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
    return builder.toString().trim()
}

/** True when the text would start with a combining mark and so draw it on a dotted circle. */
internal fun startsWithCombiningMark(text: String): Boolean =
    text.firstOrNull()?.let(::isCombiningMark) == true
