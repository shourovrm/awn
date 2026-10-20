package app.awn.quran.core.data.search

import java.text.Normalizer

/** Text reduced for matching, with the position of every kept character in the original text. */
class NormalisedText(val text: String, private val originalIndexOfKept: IntArray) {

    /**
     * Returns the range of the original text that holds the normalised characters [start] until
     * [endExclusive]. Marks that follow the last character stay inside the range, so a highlighted
     * word keeps its vowel signs.
     */
    fun originalRange(original: String, start: Int, endExclusive: Int): IntRange {
        val first = originalIndexOfKept[start]
        var last = originalIndexOfKept[endExclusive - 1]
        while (last + 1 < original.length && SearchNormaliser.isIgnorableMark(original[last + 1])) {
            last += 1
        }
        return first..last
    }
}

/**
 * Makes two spellings of the same word equal before they are compared.
 *
 * Arabic: vowel signs, Quranic annotation marks and the tatweel (the stretching line) are dropped;
 * every alef form (with hamza above or below, with madda, or alef wasla) becomes a plain alef; alef
 * maqsura becomes ya, because the Uthmani text writes موسى where a keyboard writes موسي. Latin:
 * letters are lower-cased and lose their accents, so "Rahman" finds "Raḥmān". Other scripts, Bangla
 * in particular, pass through unchanged because their vowel signs are letters, not decoration.
 */
object SearchNormaliser {

    private const val TATWEEL = 'ـ'
    private const val ALEF = 'ا'
    private const val YA = 'ي'
    private const val ALEF_MAQSURA = 'ى'
    private val alefForms = setOf('آ', 'أ', 'إ', 'ٱ', 'ٳ')

    // Harakat and related signs, the dagger alef, and the Quranic annotation marks.
    private val markRanges = listOf('ً'..'ٟ', 'ٰ'..'ٰ', 'ۖ'..'ۭ', '࣓'..'ࣿ')

    /** The first character in the range of Latin letters that carry accents. */
    private const val LATIN_ACCENTED_START = 'À'
    private const val LATIN_EXTENDED_END = 'ɏ'
    private const val LATIN_ADDITIONAL_START = 'Ḁ'
    private const val LATIN_ADDITIONAL_END = 'ỿ'
    private const val SPACING_MODIFIER_START = 'ʰ'
    private const val SPACING_MODIFIER_END = '˿'
    private const val ASCII_LIMIT = '\u0080'

    private val apostrophes = setOf('\'', '‘', '’', '`')
    private val dashes = setOf('-', '‐', '‑', '–', '—')

    /** True for a character that [normalise] drops without replacing it. */
    fun isIgnorableMark(character: Char): Boolean =
        character == TATWEEL || markRanges.any { character in it }

    fun normalise(text: String): String = normaliseWithPositions(text).text

    fun normaliseWithPositions(text: String): NormalisedText {
        val kept = StringBuilder(text.length)
        val positions = IntArray(text.length * MAX_EXPANSION)
        text.forEachIndexed { index, character ->
            val before = kept.length
            appendNormalised(character, kept)
            for (offset in before until kept.length) {
                positions[offset] = index
            }
        }
        return collapseSpaces(kept, positions)
    }

    private fun appendNormalised(character: Char, into: StringBuilder) {
        when {
            isIgnorableMark(character) -> Unit
            character in alefForms -> into.append(ALEF)
            character == ALEF_MAQSURA -> into.append(YA)
            character in apostrophes -> Unit
            character in dashes || character.isWhitespace() -> into.append(' ')
            character < ASCII_LIMIT -> into.append(character.lowercaseChar())
            character in SPACING_MODIFIER_START..SPACING_MODIFIER_END -> Unit
            isAccentedLatin(character) -> appendWithoutAccents(character, into)
            else -> into.append(character.lowercaseChar())
        }
    }

    private fun isAccentedLatin(character: Char): Boolean =
        character in LATIN_ACCENTED_START..LATIN_EXTENDED_END ||
            character in LATIN_ADDITIONAL_START..LATIN_ADDITIONAL_END

    private fun appendWithoutAccents(character: Char, into: StringBuilder) {
        val decomposed = Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
        for (part in decomposed) {
            if (Character.getType(part) != Character.NON_SPACING_MARK.toInt()) {
                into.append(part.lowercaseChar())
            }
        }
    }

    /** Collapses runs of spaces and removes leading ones, keeping the position map in step. */
    private fun collapseSpaces(text: StringBuilder, positions: IntArray): NormalisedText {
        val collapsed = StringBuilder(text.length)
        val collapsedPositions = IntArray(text.length)
        for (index in text.indices) {
            val character = text[index]
            val isRepeatedSpace =
                character == ' ' && (collapsed.isEmpty() || collapsed.last() == ' ')
            if (!isRepeatedSpace) {
                collapsedPositions[collapsed.length] = positions[index]
                collapsed.append(character)
            }
        }
        if (collapsed.isNotEmpty() && collapsed.last() == ' ')
            collapsed.setLength(collapsed.length - 1)
        return NormalisedText(collapsed.toString(), collapsedPositions)
    }

    /** One character expands to at most this many (a Latin letter that decomposes into three). */
    private const val MAX_EXPANSION = 4
}
