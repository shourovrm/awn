package app.awn.quran.core.ui

/** The text rules behind the design-system gates, kept apart so they can be tested directly. */
object TokenRules {

    private val allowedDpLiterals = setOf("0", "1", "2", "4")
    private val dpLiteral = Regex("""(\d+(?:\.\d+)?)\.dp\b""")
    private val spLiteral = Regex("""\d[\d_.]*\.sp\b""")
    private val colorLiteral = Regex("""\bColor\(\s*0x""")

    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("""//[^\n]*""")

    /** Violations as human-readable messages; empty when the source obeys the token rules. */
    fun violationsIn(kotlinSource: String): List<String> {
        val code = kotlinSource.replace(blockComment, "").replace(lineComment, "")
        val violations = mutableListOf<String>()
        dpLiteral
            .findAll(code)
            .filter { it.groupValues[1] !in allowedDpLiterals }
            .forEach { violations += "dp literal '${it.value}' (use AwnSpacing)" }
        spLiteral.findAll(code).forEach {
            violations += "sp literal '${it.value}' (use AwnTypography)"
        }
        colorLiteral.findAll(code).forEach { violations += "Color(0x literal (use AwnColors)" }
        return violations
    }
}
