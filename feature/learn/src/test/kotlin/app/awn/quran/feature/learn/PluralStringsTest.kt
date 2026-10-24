package app.awn.quran.feature.learn

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eleven Learn strings with a count were plain strings with lint's plural warning switched off, so
 * English read "1 words revised" and "back in 1 days". Counted nouns use <plurals> (English one and
 * other; Bangla other), and no string in the module hides the warning.
 */
class PluralStringsTest {

    private val english = File("src/main/res/values/strings.xml").readText()
    private val bangla = File("src/main/res/values-bn/strings.xml").readText()

    @Test
    fun noStringHidesThePluralWarning() {
        assertTrue(!english.contains("PluralsCandidate"))
        assertTrue(!bangla.contains("PluralsCandidate"))
    }

    @Test
    fun theCountedLinesArePlurals() {
        val counted =
            listOf(
                "end_summary",
                "end_tomorrow_detail",
                "word_state_known_back",
            )
        counted.forEach { name ->
            assertTrue(
                "$name should be a <plurals> in English",
                english.contains("<plurals name=\"$name"),
            )
        }
    }

    @Test
    fun englishPluralsHaveAOneForm() {
        val pluralBlocks =
            Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        pluralBlocks.findAll(english).forEach { block ->
            assertTrue(
                "${block.groupValues[1]} has no quantity=\"one\"",
                block.groupValues[2].contains("quantity=\"one\""),
            )
        }
    }
}
