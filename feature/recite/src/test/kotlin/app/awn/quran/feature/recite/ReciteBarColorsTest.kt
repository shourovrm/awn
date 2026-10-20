package app.awn.quran.feature.recite

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciteBarColorsTest {

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun theBarTakesItsBackgroundAndTextFromTheActiveModeInEveryMode() {
        for (mode in ColourMode.entries) {
            val field = awnFieldColors(AwnField.Paper, mode)

            val colors = ReciteBarColors.of(field)

            assertEquals("$mode background", field.barBackground, colors.background)
            assertEquals("$mode content", field.onBar, colors.content)
        }
    }

    @Test
    fun theMainButtonStandsOutFromTheBarInEveryMode() {
        for (mode in ColourMode.entries) {
            val colors = ReciteBarColors.of(awnFieldColors(AwnField.Paper, mode))

            assertTrue("$mode fill on bar", contrast(colors.primaryFill, colors.background) >= 3f)
            assertTrue("$mode icon on fill", contrast(colors.onPrimary, colors.primaryFill) >= 4.5f)
        }
    }

    @Test
    fun textOnTheBarIsReadableInEveryMode() {
        for (mode in ColourMode.entries) {
            val colors = ReciteBarColors.of(awnFieldColors(AwnField.Paper, mode))

            assertTrue("$mode text on bar", contrast(colors.content, colors.background) >= 4.5f)
        }
    }
}
