package app.awn.quran.feature.recite

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The mistake card squeezed the word between Listen and Close in the Latin title style, so a long
 * word broke over two lines and was hard to read. The word now has its own line in the Quran font
 * and shrinks to fit instead of wrapping, also on a narrow phone with large text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class ReviewCardWordTest {
    @get:Rule val composeRule = createComposeRule()

    // One of the longest single words in the Quran (2:137).
    private val longWord = "فَسَيَكْفِيكَهُمُ"
    private val key = WordKey(2, 137, 14)

    private fun showCard(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AwnTheme {
                    ReciteReviewCard(
                        reviews = listOf(WordReview(key, listOf(ReviewFinding.WrongWord))),
                        focusedKey = key,
                        words = listOf(Word(key, longWord, codeV1 = null, codeV2 = null)),
                        onFocus = {},
                        onListen = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun wordLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(longWord).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { action ->
            action(layouts)
        }
        return layouts.single()
    }

    @Test
    fun aLongWordStaysOnOneLine() {
        showCard(fontScale = 1.0f)

        assertEquals(1, wordLayout().lineCount)
    }

    @Test
    fun aLongWordStaysOnOneLineWithLargeText() {
        showCard(fontScale = 1.3f)

        assertEquals(1, wordLayout().lineCount)
    }

    @Test
    fun theWordIsDrawnInTheQuranFont() {
        showCard(fontScale = 1.0f)

        assertEquals(
            AwnTypography.quran.fontFamily,
            wordLayout().layoutInput.style.fontFamily,
        )
    }
}
