package app.awn.quran.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate from docs/design-system.md: sizes and colours come only from the theme package. */
class ThemeTokenGateTest {

    @Test
    fun noShippedCodeOutsideTheThemeUsesRawSizesOrColours() {
        val offences =
            RepositorySources.shippedKotlinFiles()
                .filterNot(RepositorySources::isThemeFile)
                .flatMap { file ->
                    TokenRules.violationsIn(file.readText()).map {
                        "${RepositorySources.relativePath(file)}: $it"
                    }
                }

        assertTrue("Token rule violations:\n${offences.joinToString("\n")}", offences.isEmpty())
    }

    @Test
    fun theGateInspectsTheThemeAndAtLeastOneOtherFile() {
        val files = RepositorySources.shippedKotlinFiles()

        assertTrue(files.any(RepositorySources::isThemeFile))
        assertTrue(files.any { !RepositorySources.isThemeFile(it) })
    }

    @Test
    fun allowedDpLiteralsPass() {
        val source = "val a = 0.dp; val b = 1.dp; val c = 2.dp; val d = 4.dp"

        assertEquals(emptyList<String>(), TokenRules.violationsIn(source))
    }

    @Test
    fun otherDpLiteralsFail() {
        assertEquals(1, TokenRules.violationsIn("val a = 16.dp").size)
        assertEquals(1, TokenRules.violationsIn("val a = 0.5.dp").size)
        assertEquals(1, TokenRules.violationsIn("val a = 14.dp").size)
    }

    @Test
    fun spLiteralsFail() {
        assertEquals(1, TokenRules.violationsIn("val a = 14.sp").size)
    }

    @Test
    fun colorHexLiteralsFail() {
        assertEquals(1, TokenRules.violationsIn("val a = Color(0xFF112233)").size)
    }

    @Test
    fun commentsAreIgnored() {
        val source = "// 16.dp is the margin\n/* Color(0xFF0 */ val a = 0.dp"

        assertEquals(emptyList<String>(), TokenRules.violationsIn(source))
    }
}
