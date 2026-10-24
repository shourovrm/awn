package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The stopping lesson showed أَحَدْ as أَحَ and cut the marks under the letters (2026-10-24).
 * Native graphics shape the real Hafs font, so these tests see the line break and the line box that
 * the phone had.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class WordTileTextTest {

    @get:Rule val composeRule = createComposeRule()

    private val hafs =
        FontFamily(
            Font(File("../../core/data/src/main/assets/packs/UthmanicHafs1Ver18.ttf").absoluteFile),
        )

    /** Draws every word at every width in one screen, because a test can set content once. */
    private fun layoutsOf(words: List<String>, widthsDp: List<Int>): Map<String, TextLayoutResult> {
        val layouts = mutableMapOf<String, TextLayoutResult>()
        composeRule.setContent {
            AwnTheme {
                val largest = AwnTypography.quran.fontSize * 1.4f
                Column {
                    for (word in words) {
                        for (width in widthsDp) {
                            Box(Modifier.width(width.dp)) {
                                BasicText(
                                    text = word,
                                    style = wordTileTextStyle(hafs, largest, Color.Black),
                                    maxLines = 1,
                                    softWrap = false,
                                    onTextLayout = { layouts["$word at $width dp"] = it },
                                    autoSize =
                                        TextAutoSize.StepBased(
                                            minFontSize = AwnTypography.lead.fontSize,
                                            maxFontSize = largest,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return layouts
    }

    @Test
    fun theStyleHasTheLineHeightAndNoLetterSpacingOfQuranText() {
        val style = wordTileTextStyle(hafs, TextUnit.Unspecified, Color.Black)

        assertEquals(AwnTypography.quran.lineHeight, style.lineHeight)
        assertTrue(style.letterSpacing == TextUnit.Unspecified)
    }

    @Test
    fun wholeWordsStayOnOneLineAtEveryTileWidth() {
        val words = listOf("أَحَدْ", "ٱلْفَلَقْ", "إِذَا هَوَىٰ")
        val widths = listOf(120, 128, 144)

        val layouts = layoutsOf(words, widths)

        assertEquals(words.size * widths.size, layouts.size)
        for ((name, layout) in layouts) {
            assertEquals(name, 1, layout.lineCount)
            assertFalse("$name overflows", layout.didOverflowWidth)
            assertTrue(name, layout.layoutInput.text.text in words)
        }
    }
}
