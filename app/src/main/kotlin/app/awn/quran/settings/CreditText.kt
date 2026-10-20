package app.awn.quran.settings

import java.net.URI
import java.net.URISyntaxException

/** Turns the raw text of a manifest credit into text a person can read. */
object CreditText {

    private val tag = Regex("<[^>]*>")
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val whitespace = Regex("\\s+")

    private val namedEntities =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "ndash" to "–",
            "mdash" to "—",
            "lsquo" to "‘",
            "rsquo" to "’",
            "ldquo" to "“",
            "rdquo" to "”",
            "hellip" to "…",
        )

    /**
     * Removes markup tags, decodes named and numeric HTML entities (`&amp;`, `&#39;`, `&#x27;`) and
     * collapses runs of white space. An entity that is not known stays as written.
     */
    fun toPlainText(raw: String): String =
        tag.replace(raw, " ")
            .let { entity.replace(it) { match -> decode(match.groupValues[1]) ?: match.value } }
            .replace(whitespace, " ")
            .trim()

    private fun decode(name: String): String? =
        when {
            name.startsWith("#x") -> codePointText(name.drop(2), HEXADECIMAL)
            name.startsWith("#") -> codePointText(name.drop(1), DECIMAL)
            else -> namedEntities[name]
        }

    private fun codePointText(digits: String, radix: Int): String? {
        val codePoint = digits.toIntOrNull(radix) ?: return null
        return if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint))
        else null
    }

    /**
     * A short label for a link: the host without "www.", so a long GitHub path reads "github.com".
     * A value that is not a web address is returned as written.
     */
    fun linkLabel(url: String): String {
        val host =
            try {
                URI(url.trim()).host
            } catch (_: URISyntaxException) {
                null
            }
        return host?.removePrefix("www.") ?: url.trim()
    }

    private const val HEXADECIMAL = 16
    private const val DECIMAL = 10
}
