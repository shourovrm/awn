package app.awn.quran.core.data.search

/**
 * Folds the spelling variants of Bangla that readers type interchangeably, so "ইয়াসিন" finds
 * "ইয়াসীন" and "ধীর" finds "ধির". It is applied letter by letter inside [SearchNormaliser], for
 * ayah translations and for surah names alike.
 *
 * What is folded, and why:
 * - Long and short vowels (ী with ি, ূ with ু, ঈ with ই, ঊ with উ): Bangla pronounces them the
 *   same, and keyboards, printers and translators choose between them freely.
 * - ৎ becomes ত and ং becomes ঙ: the khanda ta and the anusvara are written either way for the same
 *   sound (রং and রঙ).
 * - The nukta (়) is dropped and the three precomposed letters ড় ঢ় য় become ড ঢ য: the same
 *   letter is stored as one code point by some keyboards and as two by others, and unifying both
 *   forms to the base letter makes them equal whichever way it was typed.
 * - The hasanta (্), the chandrabindu (ঁ), the visarga (ঃ) and the zero-width joiners are dropped:
 *   the hasanta only joins consonants into a conjunct, whose letters stay in the same order, and
 *   publishers differ on whether a conjunct is written with a visible hasanta or a joiner.
 *
 * Not folded: vowels of different quality (া, ে, ো), consonants that only sound alike (শ ষ স, জ য),
 * and the digits. A name search may fold those further; ayah text must not, because it would join
 * different words.
 */
internal object BanglaSpelling {

    private const val ZERO_WIDTH_NON_JOINER = '\u200C'
    private const val ZERO_WIDTH_JOINER = '\u200D'
    private const val HASANTA = '\u09CD'
    private const val NUKTA = '\u09BC'
    private const val CHANDRABINDU = '\u0981'
    private const val VISARGA = '\u0983'
    private val bengaliBlock = '\u0980'..'\u09FF'

    private val replacements =
        mapOf(
            '\u09C0' to '\u09BF', // ী to ি
            '\u09C2' to '\u09C1', // ূ to ু
            '\u0988' to '\u0987', // ঈ to ই
            '\u098A' to '\u0989', // ঊ to উ
            '\u09CE' to '\u09A4', // ৎ to ত
            '\u0982' to '\u0999', // ং to ঙ
            '\u09DC' to '\u09A1', // ড় to ড
            '\u09DD' to '\u09A2', // ঢ় to ঢ
            '\u09DF' to '\u09AF', // য় to য
        )

    private val dropped =
        setOf(HASANTA, NUKTA, CHANDRABINDU, VISARGA, ZERO_WIDTH_NON_JOINER, ZERO_WIDTH_JOINER)

    /**
     * Appends the folded form of [character] to [into] when it is a Bangla letter or sign or a
     * joiner, and returns whether it did. A dropped character appends nothing and still counts as
     * handled.
     */
    fun appendFolded(character: Char, into: StringBuilder): Boolean {
        val isHandled = character in bengaliBlock || character in dropped
        if (!isHandled) return false
        if (character !in dropped) into.append(replacements[character] ?: character)
        return true
    }
}
