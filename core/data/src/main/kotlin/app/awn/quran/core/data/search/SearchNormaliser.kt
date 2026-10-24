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
 * How the dagger alef (the small alef above a letter, U+0670) is read. The Uthmani text writes a
 * long "a" as a dagger alef where a keyboard writes a full alef (ٱلرَّحْمَٰن against الرحمان), but
 * it also writes it where a keyboard writes nothing (ذَٰلِكَ against ذلك), so one reading cannot
 * serve both. A text is therefore compared in all three readings and a query matches when any
 * reading contains it.
 */
enum class DaggerAlefReading {
    /** The dagger alef is dropped: ذَٰلِكَ is ذلك, ٱلرَّحْمَٰنِ is الرحمن. */
    Dropped,

    /**
     * The dagger alef is an alef after its letter: ٱلرَّحْمَٰنِ is الرحمان, ٱلسَّمَٰوَٰتِ is
     * السماوات.
     */
    Written,

    /**
     * Like [Written], but a dagger alef on a waw or ya inside a word stands in for that letter:
     * ٱلصَّلَوٰةَ is الصلاة and ٱلتَّوْرَىٰةَ is التوراة, where keyboard spelling has an alef and
     * no waw or ya.
     */
    ReplacingCarrier,
}

/**
 * Makes two spellings of the same word equal before they are compared.
 *
 * Arabic: vowel signs, Quranic annotation marks and the tatweel (the stretching line) are dropped;
 * every alef form (with hamza above or below, with madda, or alef wasla) becomes a plain alef; alef
 * maqsura becomes ya, because the Uthmani text writes موسى where a keyboard writes موسي. The hamza
 * on a waw or ya seat becomes a bare hamza, and a bare hamza before an alef is dropped, so
 * ءَامَنُوا, آمنوا and امنوا agree and رَءُوف agrees with رؤوف. Uthmani letters that only the
 * Uthmani spelling has are read in [DaggerAlefReading] terms (see [normalise]). Latin: letters are
 * lower-cased and lose their accents, so "Rahman" finds "Raḥmān". Bangla keeps its vowel signs,
 * which are letters, but folds the spellings readers use interchangeably (see [BanglaSpelling]).
 * Other scripts pass through unchanged.
 */
object SearchNormaliser {

    internal const val TATWEEL = 'ـ'
    internal const val ALEF = 'ا'
    internal const val YA = 'ي'
    private const val ALEF_MAQSURA = 'ى'
    private const val HAMZA = 'ء'
    internal const val SPACE = ' '
    private val hamzaSeats = setOf('ؤ', 'ئ')
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

    /**
     * The text in one reading of the dagger alef; [DaggerAlefReading.Dropped] suits Latin and
     * Bangla too.
     */
    fun normalise(
        text: String,
        reading: DaggerAlefReading = DaggerAlefReading.Dropped,
    ): String = normaliseWithPositions(text, reading).text

    /**
     * A word-final alef before ta marbuta, as in الصلاة and الزكاة, which the Uthmani spelling
     * writes with a waw.
     */
    private val alefBeforeFinalTaMarbuta = Regex("اة(?=\\s|$)")

    /**
     * The normalised forms of a typed query. Keyboard spelling writes the long "a" before a final
     * ta marbuta with an alef (الصلاة) where the Uthmani spelling has a waw (الصلوة), so a query
     * that ends a word that way also has a second form with the waw.
     */
    fun normalisedQueries(query: String): List<String> {
        val plain = normalise(query)
        val withWaw = plain.replace(alefBeforeFinalTaMarbuta, "وة")
        return listOf(plain, withWaw).distinct()
    }

    /**
     * The distinct normalised forms of [text], one per [DaggerAlefReading]. A text without a dagger
     * alef, small high yeh or hamza-above sign has one form, so translations cost no extra memory.
     */
    fun normalisedReadings(text: String): List<String> =
        DaggerAlefReading.entries.map { normalise(text, it) }.distinct()

    fun normaliseWithPositions(
        text: String,
        reading: DaggerAlefReading = DaggerAlefReading.Dropped,
    ): NormalisedText {
        val kept = StringBuilder(text.length)
        val positions = IntArray(text.length * MAX_EXPANSION)
        text.forEachIndexed { index, character ->
            val before = kept.length
            appendNormalised(text, index, reading, kept)
            for (offset in before until kept.length) {
                positions[offset] = index
            }
        }
        return compact(kept, positions)
    }

    private fun appendNormalised(
        text: String,
        index: Int,
        reading: DaggerAlefReading,
        into: StringBuilder,
    ) {
        val character = text[index]
        if (
            reading != DaggerAlefReading.Dropped &&
                UthmaniLetters.appendSpelledOut(text, index, reading, into)
        ) {
            return
        }
        if (BanglaSpelling.appendFolded(character, into)) return
        appendOtherScripts(character, into)
    }

    // Arabic letters and marks, punctuation and Latin, after the Uthmani and Bangla rules.
    private fun appendOtherScripts(character: Char, into: StringBuilder) {
        when {
            isIgnorableMark(character) -> Unit
            character in alefForms -> into.append(ALEF)
            character == ALEF_MAQSURA -> into.append(YA)
            character in hamzaSeats -> into.append(HAMZA)
            character in apostrophes -> Unit
            character in dashes || character.isWhitespace() -> into.append(SPACE)
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

    /**
     * Collapses runs of spaces, removes leading ones and drops a bare hamza that is followed by an
     * alef (the Uthmani ءَامَنُوا against the keyboard امنوا), keeping the position map in step.
     */
    private fun compact(text: StringBuilder, positions: IntArray): NormalisedText {
        val compacted = StringBuilder(text.length)
        val compactedPositions = IntArray(text.length)
        for (index in text.indices) {
            val character = text[index]
            val isRepeatedSpace =
                character == SPACE && (compacted.isEmpty() || compacted.last() == SPACE)
            val isHamzaBeforeAlef =
                character == HAMZA && index + 1 < text.length && text[index + 1] == ALEF
            if (!isRepeatedSpace && !isHamzaBeforeAlef) {
                compactedPositions[compacted.length] = positions[index]
                compacted.append(character)
            }
        }
        if (compacted.isNotEmpty() && compacted.last() == SPACE)
            compacted.setLength(compacted.length - 1)
        return NormalisedText(compacted.toString(), compactedPositions)
    }

    /** One character expands to at most this many (a Latin letter that decomposes into three). */
    private const val MAX_EXPANSION = 4
}
