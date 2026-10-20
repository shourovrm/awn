package app.awn.quran.core.ui

/** The text rules behind the copy gate. */
object CopyRules {

    private val quotedUiLiterals =
        listOf(
            Regex("""\bText\(\s*(?:text\s*=\s*)?""""),
            Regex("""\bcontentDescription\s*=\s*""""),
            Regex("""\blabel\s*=\s*""""),
            Regex("""\bplaceholder\s*=\s*""""),
        )

    private val bannedWords =
        Regex(
            "\\b(" +
                "model|threshold|session|engine|alignment|buffer|sync|verify|" +
                "fallback|pack|tracker|phoneme|cache|manifest" +
                ")s?\\b",
            RegexOption.IGNORE_CASE,
        )
    private val bannedAbbreviation = Regex("""\bAI\b""")

    private val stringEntry =
        Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** The AI-advisory notice is the one place those words are required. */
    private const val NOTICE_STRING_PREFIX = "notice_"

    fun hardCodedTextIn(kotlinSource: String): List<String> = quotedUiLiterals.flatMap { pattern ->
        pattern.findAll(kotlinSource).map { "quoted text after '${it.value.trim()}'" }.toList()
    }

    fun bannedWordsIn(stringsXml: String): List<String> =
        stringEntry
            .findAll(stringsXml)
            .filterNot { it.groupValues[1].startsWith(NOTICE_STRING_PREFIX) }
            .flatMap { entry ->
                val name = entry.groupValues[1]
                val text = entry.groupValues[2]
                val words =
                    bannedWords.findAll(text).map { it.value } +
                        bannedAbbreviation.findAll(text).map { it.value }
                words.map { "string '$name' contains banned word '$it'" }
            }
            .toList()
}
