package app.awn.quran.feature.learn.course

import java.util.Locale

/**
 * Picks the meaning that fits a word from the glosses the word-by-word pack gives it at its
 * different places. One place can carry a gloss that only fits its context: إِلَىٰ is "with" at
 * 2:14 and "to" at most of its other places.
 *
 * Glosses are compared without their bracketed parts, which the pack uses for words the translator
 * added for English or Bangla grammar ("(of) Allah" and "Allah" are one gloss), and without case or
 * extra spaces. The commonest gloss wins; a tie goes to the one met first.
 *
 * The winner is shown as its commonest writing with the brackets at its edges dropped: "(of) Allah"
 * reads as a label for the word as "Allah". Brackets inside a phrase stay, because dropping them
 * would change the phrase ("the (people of) Aad" is not "the Aad").
 */
internal object CommonGloss {

    private val bracketed = Regex("""[(\[][^)\]]*[)\]]""")
    private val leadingBrackets = Regex("""^(\s*[(\[][^)\]]*[)\]])+\s*""")
    private val trailingBrackets = Regex("""\s*([(\[][^)\]]*[)\]]\s*)+$""")
    private val whitespace = Regex("""\s+""")

    /** The gloss to show for [glosses], or null when none of them has text outside brackets. */
    fun of(glosses: List<String>): String? {
        val writingsByVoteKey = LinkedHashMap<String, MutableList<String>>()
        for (gloss in glosses) {
            val voteKey = voteKeyOf(gloss)
            if (voteKey.isNotEmpty()) {
                writingsByVoteKey.getOrPut(voteKey) { mutableListOf() }.add(gloss)
            }
        }
        val winningWritings = writingsByVoteKey.values.maxByOrNull { it.size } ?: return null
        val commonestWriting = winningWritings.groupingBy { it }.eachCount().maxBy { it.value }.key
        return withoutEdgeBrackets(commonestWriting)
    }

    private fun voteKeyOf(gloss: String): String =
        gloss.replace(bracketed, " ").replace(whitespace, " ").trim().lowercase(Locale.ROOT)

    private fun withoutEdgeBrackets(gloss: String): String =
        gloss.replace(leadingBrackets, "").replace(trailingBrackets, "").trim()
}
