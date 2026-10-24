package app.awn.quran.search

import app.awn.quran.core.data.search.SearchNormaliser

private const val BENGALI_FIRST = '\u0980'
private const val BENGALI_LAST = '\u09FF'
private val baFola = Regex("\u09CD\u09AC")

// The word "surah" in front of a name ("সূরা মুলক"), after folding ূ to ু. The optional হ is the
// spelling সূরাহ.
private val leadingSuraWord = Regex("^\u09B8\u09C1\u09B0\u09BE\u09B9?\\s*")

// The article of a Bangla name, after folding: আল, the sun-letter forms (আন, আস, আত, আদ, আর, আজ)
// and
// the construct form আলে ("আলে ইমরান"). It must be followed by a space, because the article is
// written as a separate word.
private val banglaArticle =
    Regex("^\u0986(\u09B2\u09BF|[\u09B2\u09A8\u09B8\u09A4\u09A6\u09B0\u099C])\\s+")

private val soundAlikes =
    mapOf(
        '\u09C7' to '\u09BF', // ে to ি
        '\u09CB' to '\u09C1', // ো to ু
        '\u09B6' to '\u09B8', // শ to স
        '\u09B7' to '\u09B8', // ষ to স
        '\u099B' to '\u09B8', // ছ to স
        '\u09AF' to '\u099C', // য to জ
        '\u09AD' to '\u09AC', // ভ to ব
        '\u09A3' to '\u09A8', // ণ to ন
    )

/**
 * Folded text with its word breaks kept, so a leading article or "surah" can be found. Surah names,
 * and what a user types to find them, are reduced to one comparable spelling.
 *
 * [SearchNormaliser] already unifies the spellings that Bangla ayah text shares with names (long
 * and short vowels, ৎ and ং, the nukta, the hasanta). Names need more, because a Bangla surah name
 * is a transliteration of an Arabic word and no two printers transliterate alike. These further
 * folds apply to names only; applied to ayah text they would join different words:
 * - ে with ি and ো with ু: ফাতেহা and ফাতিহা, মোহাম্মদ and মুহাম্মদ.
 * - শ, ষ and ছ with স: ইখলাস, ইখলাছ and ইখলাশ.
 * - য with জ and ভ with ব: যুমার and জুমার, তাকভীর and তাকবীর.
 * - ণ with ন: the Arabic nun is written with either.
 * - ব after a hasanta is dropped (ক্বাফ is কাফ, ত্বীন is তীন): it only marks the Arabic letters ق
 *   and ط, which a reader hears as ক and ত.
 * - A doubled Bangla letter becomes one letter (সাফফাত and সাফাত), because the shadda is spelled
 *   with a conjunct by some printers and left out by others.
 */
internal fun foldedWords(text: String): String {
    val normalised = SearchNormaliser.normalise(text.replace(baFola, ""))
    return normalised.map { soundAlikes[it] ?: it }.joinToString("")
}

/** [folded] without punctuation and spaces, and with doubled Bangla letters written once. */
internal fun squeezed(folded: String): String {
    val squeezedText = StringBuilder(folded.length)
    for (character in folded) {
        if (!isLetterDigitOrMark(character)) continue
        val repeatsBanglaLetter =
            character.isLetter() &&
                character in BENGALI_FIRST..BENGALI_LAST &&
                squeezedText.lastOrNull() == character
        if (!repeatsBanglaLetter) squeezedText.append(character)
    }
    return squeezedText.toString()
}

private fun isLetterDigitOrMark(character: Char): Boolean {
    if (character.isLetterOrDigit()) return true
    val type = Character.getType(character)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt()
}

/**
 * [folded] without a leading "surah". A query that is only that word is kept, because it also reads
 * as the name of surah 42 (শূরা folds to the same letters as সূরা).
 */
internal fun withoutSuraWord(folded: String): String {
    val withoutWord = folded.replace(leadingSuraWord, "")
    return withoutWord.ifBlank { folded }
}

internal fun withoutBanglaArticle(folded: String): String = folded.replace(banglaArticle, "")
