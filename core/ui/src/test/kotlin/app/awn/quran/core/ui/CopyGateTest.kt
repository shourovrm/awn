package app.awn.quran.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate from docs/design-system.md: visible text lives in strings.xml and avoids jargon. */
class CopyGateTest {

    @Test
    fun noShippedCodePassesQuotedTextToTheUi() {
        val offences =
            RepositorySources.shippedKotlinFiles().flatMap { file ->
                CopyRules.hardCodedTextIn(file.readText()).map {
                    "${RepositorySources.relativePath(file)}: $it"
                }
            }

        assertTrue("Hard-coded UI text:\n${offences.joinToString("\n")}", offences.isEmpty())
    }

    @Test
    fun noStringResourceUsesABannedWordOutsideTheNotice() {
        val files = RepositorySources.stringResourceFiles()
        assertTrue("No strings.xml found", files.isNotEmpty())

        val offences = files.flatMap { file ->
            CopyRules.bannedWordsIn(file.readText()).map {
                "${RepositorySources.relativePath(file)}: $it"
            }
        }

        assertTrue("Banned words:\n${offences.joinToString("\n")}", offences.isEmpty())
    }

    @Test
    fun quotedTextInUiCallsIsFound() {
        assertEquals(1, CopyRules.hardCodedTextIn("""Text("Hello")""").size)
        assertEquals(1, CopyRules.hardCodedTextIn("""Text(text = "Hello")""").size)
        assertEquals(1, CopyRules.hardCodedTextIn("""Icon(contentDescription = "Play")""").size)
        assertEquals(1, CopyRules.hardCodedTextIn("""Field(label = "Name")""").size)
        assertEquals(1, CopyRules.hardCodedTextIn("""Field(placeholder = "Search")""").size)
    }

    @Test
    fun textFromResourcesAndParametersIsAccepted() {
        assertEquals(0, CopyRules.hardCodedTextIn("Text(stringResource(R.string.a))").size)
        assertEquals(0, CopyRules.hardCodedTextIn("Text(text = label)").size)
    }

    @Test
    fun bannedWordsAreFoundOutsideTheNoticeOnly() {
        val xml =
            """
            <resources>
              <string name="about_body">Uses an AI model to sync</string>
              <string name="notice_english">An AI model that may be wrong</string>
              <string name="fine">Settings</string>
            </resources>
            """
                .trimIndent()

        val offences = CopyRules.bannedWordsIn(xml)

        assertEquals(3, offences.size)
        assertTrue(offences.all { "about_body" in it })
    }
}
