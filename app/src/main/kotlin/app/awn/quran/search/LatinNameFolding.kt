package app.awn.quran.search

// The Latin spellings of the Arabic article: al, and its assimilated forms before the sun letters
// (ar-Rahman, ash-Shuraa, adh-Dhariyat). Longer spellings come first so "ash" is not read as "as".
private const val ARTICLE_SPELLINGS = "adh|ash|ath|al|ad|an|ar|as|at|az"

// A typed article may be followed by a space or a hyphen, or by nothing ("alrahman"). With nothing
// between, at least three letters must follow, so "anam" is not read as article "an" and "am".
private val typedLatinArticle = Regex("^($ARTICLE_SPELLINGS)(?:[\\s-]+|(?=[a-z]{3}))")

private val longEnglishVowels = mapOf("ee" to "i", "oo" to "u")
private const val FIRST_LATIN_LETTER = 'a'
private const val LAST_LATIN_LETTER = 'z'

/**
 * [folded] without a leading Latin article. A user types "alrahman", "al-rahman" or "rahman" for
 * Ar-Rahman, and "arrahman" because the name is spelled that way, so the article is optional and is
 * cut whether or not a space follows.
 */
internal fun withoutLatinArticle(folded: String): String = folded.replace(typedLatinArticle, "")

/**
 * The transliteration reduced to one spelling, so variants of the same sound are equal: "yaseen"
 * and "yasin", "ikhlaas" and "ikhlas", "mulq" and "mulk", "rahmaan" and "rahman". "dh", "th", "sh"
 * and "kh" are kept as written because they stand for different Arabic letters. Letters outside a
 * to z (Arabic, Bangla) pass through unchanged.
 */
internal fun withLatinVariantsFolded(squeezedText: String): String {
    var text = squeezedText
    for ((spelling, replacement) in longEnglishVowels) {
        text = text.replace(spelling, replacement)
    }
    text = text.replace('q', 'k')
    return withDoubledLatinLettersOnce(text)
}

/** [text] with a repeated Latin letter written once: "ar-rahman" is "arahman" in both spellings. */
private fun withDoubledLatinLettersOnce(text: String): String {
    val result = StringBuilder(text.length)
    for (character in text) {
        val isLatinLetter = character in FIRST_LATIN_LETTER..LAST_LATIN_LETTER
        val repeatsLastLetter = result.lastOrNull() == character
        if (!(isLatinLetter && repeatsLastLetter)) result.append(character)
    }
    return result.toString()
}

/** True when [text] is made of Latin letters only, the case in which a typo is forgiven. */
internal fun isLatinWord(text: String): Boolean =
    text.isNotEmpty() && text.all { it in FIRST_LATIN_LETTER..LAST_LATIN_LETTER }
