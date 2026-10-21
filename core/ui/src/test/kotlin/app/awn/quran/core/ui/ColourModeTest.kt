package app.awn.quran.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.core.ui.theme.DayColours
import app.awn.quran.core.ui.theme.NightColours
import app.awn.quran.core.ui.theme.awnFieldColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourModeTest {

    @Test
    fun followingTheDeviceUsesTheDayPaletteInTheDaytime() {
        val settings = ColourSettings(day = DayColours.Light, night = NightColours.Black)

        assertEquals(ColourMode.Light, settings.resolve(deviceIsDark = false))
    }

    @Test
    fun followingTheDeviceUsesTheNightPaletteWhenTheDeviceIsDark() {
        val settings = ColourSettings(day = DayColours.Light, night = NightColours.Black)

        assertEquals(ColourMode.Black, settings.resolve(deviceIsDark = true))
    }

    @Test
    fun theManualStateIgnoresTheDevice() {
        val manualDay = ColourSettings(followDevice = false, manualNight = false)
        val manualNight = ColourSettings(followDevice = false, manualNight = true)

        assertEquals(ColourMode.Awn, manualDay.resolve(deviceIsDark = true))
        assertEquals(ColourMode.Dark, manualNight.resolve(deviceIsDark = false))
    }

    @Test
    fun theDayNightButtonFlipsWhatIsOnScreenAndStopsFollowingTheDevice() {
        val following = ColourSettings(followDevice = true)

        val afterPressOnDarkDevice = following.toggledNight(deviceIsDark = true)

        assertFalse(afterPressOnDarkDevice.followDevice)
        assertFalse(afterPressOnDarkDevice.isNight(deviceIsDark = true))
        assertTrue(afterPressOnDarkDevice.toggledNight(deviceIsDark = true).isNight(true))
    }

    @Test
    fun onlyDarkAndBlackAreNightModes() {
        assertEquals(
            listOf(ColourMode.Dark, ColourMode.Black),
            ColourMode.entries.filter { it.isNight },
        )
    }

    @Test
    fun awnModeKeepsTodaysPaletteForEveryField() {
        assertEquals(AwnColors.paper, awnFieldColors(AwnField.Paper, ColourMode.Awn).background)
        assertEquals(AwnColors.green, awnFieldColors(AwnField.Green, ColourMode.Awn).background)
        assertEquals(AwnColors.ink, awnFieldColors(AwnField.Night, ColourMode.Awn).background)
        assertEquals(AwnColors.ochre, awnFieldColors(AwnField.Ochre, ColourMode.Awn).background)
        assertEquals(AwnColors.green, awnFieldColors(AwnField.Paper).buttonFill)
    }

    @Test
    fun theFourPagesHaveTheSpecifiedColours() {
        assertEquals(Color(0xFFFFFFFF), awnFieldColors(AwnField.Paper, ColourMode.Light).background)
        assertEquals(Color(0xFF121212), awnFieldColors(AwnField.Paper, ColourMode.Dark).background)
        assertEquals(Color(0xFF000000), awnFieldColors(AwnField.Paper, ColourMode.Black).background)
    }

    @Test
    fun lightModeBarsAreNeutralGreyAndTheAccentIsGreen() {
        val light = awnFieldColors(AwnField.Paper, ColourMode.Light)

        assertEquals(light.barBackground.red, light.barBackground.green, 0.02f)
        assertEquals(light.barBackground.green, light.barBackground.blue, 0.02f)
        assertEquals(AwnColors.green, light.buttonFill)
    }

    @Test
    fun everyModeTextContrastsWithItsPage() {
        for (mode in ColourMode.entries) {
            for (field in AwnField.entries) {
                val colors = awnFieldColors(field, mode)
                val ratio = contrastRatio(colors.background, colors.content)

                assertTrue("$mode $field contrast $ratio", ratio >= MINIMUM_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun barTextContrastsWithItsBarInEveryMode() {
        for (mode in ColourMode.entries) {
            val colors = awnFieldColors(AwnField.Paper, mode)
            val ratio = contrastRatio(colors.barBackground, colors.onBar)

            assertTrue("$mode bar contrast $ratio", ratio >= MINIMUM_TEXT_CONTRAST)
        }
    }

    @Test
    fun lightFlagMatchesTheBackgroundInEveryMode() {
        for (mode in ColourMode.entries) {
            for (field in AwnField.entries) {
                val colors = awnFieldColors(field, mode)

                assertEquals("$mode $field", colors.background.luminance() > 0.18f, colors.isLight)
            }
        }
    }

    @Test
    fun highlightFillsAreDistinctInEveryMode() {
        for (mode in ColourMode.entries) {
            val colors = awnFieldColors(AwnField.Paper, mode)
            val fills =
                setOf(
                    colors.selectedAyahFill,
                    colors.playingAyahFill,
                    colors.playingWordFill,
                    colors.bookmarkedAyahFill,
                )

            assertEquals("$mode", 4, fills.size)
        }
    }

    @Test
    fun theTextStaysReadableUnderTheStrongestFillInEveryMode() {
        for (mode in ColourMode.entries) {
            val colors = awnFieldColors(AwnField.Paper, mode)
            val strongest = colors.playingWordFill.compositeOver(colors.background)
            val ratio = contrastRatio(strongest, colors.content)

            assertTrue("$mode contrast under fill $ratio", ratio >= MINIMUM_TEXT_CONTRAST)
        }
    }

    @Test
    fun aFigureTileIsDistinctFromThePageInTheNeutralModes() {
        for (mode in listOf(ColourMode.Light, ColourMode.Dark, ColourMode.Black)) {
            val page = awnFieldColors(AwnField.Paper, mode)
            val tile = awnFieldColors(AwnField.Green, mode)

            assertNotEquals("$mode", page.background, tile.background)
        }
    }

    @Test
    fun noBarOrBandIsGreenOrBrightInTheNightModes() {
        for (mode in listOf(ColourMode.Dark, ColourMode.Black)) {
            for (field in AwnField.entries) {
                val bar = awnFieldColors(field, mode).barBackground

                assertNotEquals("$mode $field bar is the Awn green", AwnColors.green, bar)
                assertTrue(
                    "$mode $field bar is too bright: ${bar.luminance()}",
                    bar.luminance() < MAXIMUM_NIGHT_BAR_LUMINANCE,
                )
            }
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
    }

    private companion object {
        const val MINIMUM_TEXT_CONTRAST = 4.5f
        const val CONTRAST_OFFSET = 0.05f
        const val MAXIMUM_NIGHT_BAR_LUMINANCE = 0.05f
    }
}
