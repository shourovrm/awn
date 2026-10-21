package app.awn.quran.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

private val touchTarget = 48.dp

/** Renders the settings components in Awn and Black colour modes at font scales 1.0 and 1.3. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class SettingsComponentsTest(private val mode: ColourMode, private val fontScale: Float) {

    @get:Rule val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AwnTheme { Column(Modifier.width(360.dp)) { content() } }
            }
        }
    }

    @Test
    fun theSwitchTogglesAndKeepsAFortyEightDpTouchTarget() {
        var checked = false
        show {
            var state by remember { mutableStateOf(checked) }
            AwnSwitch(
                checked = state,
                onCheckedChange = {
                    state = it
                    checked = it
                },
            )
        }

        val switchNode = composeRule.onNode(hasClickAction())
        switchNode.assertWidthIsAtLeast(touchTarget).assertHeightIsAtLeast(touchTarget)
        assertEquals(
            Role.Switch,
            switchNode.fetchSemanticsNode().config[SemanticsProperties.Role],
        )
        switchNode.performClick()

        assertTrue(checked)
    }

    @Test
    fun aSwitchRowTogglesFromTheWholeRowAndShowsItsHint() {
        var checked = true
        show {
            var state by remember { mutableStateOf(checked) }
            SettingsSwitchRow(
                label = "Wi-Fi only",
                hint = "Downloads wait for Wi-Fi",
                checked = state,
                onCheckedChange = {
                    state = it
                    checked = it
                },
            )
        }

        composeRule.onNodeWithText("Downloads wait for Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi only").performClick()

        assertFalse(checked)
    }

    @Test
    fun aChoiceRowShowsItsValueOpensAMenuAndReportsTheChoice() {
        var chosen = "Page"
        show {
            var state by remember { mutableStateOf(chosen) }
            AwnChoiceRow(
                label = "Download by",
                hint = "Audio for the whole page at once",
                options = listOf("Page", "Surah", "Juz"),
                selected = state,
                optionLabel = { it },
                onSelect = {
                    state = it
                    chosen = it
                },
            )
        }

        composeRule.onNodeWithText("Audio for the whole page at once").assertIsDisplayed()
        composeRule.onNodeWithText("Download by").performClick()
        composeRule.onNodeWithText("Surah").assertIsDisplayed().assertHeightIsAtLeast(44.dp)
        composeRule.onNodeWithText("Surah").performClick()

        assertEquals("Surah", chosen)
        composeRule.onNodeWithText("Juz").assertDoesNotExist()
    }

    @Test
    fun theChosenMenuItemIsMarkedSelected() {
        show {
            AwnChoiceRow(
                label = "Download by",
                options = listOf("Page", "Surah", "Juz"),
                selected = "Surah",
                optionLabel = { it },
                onSelect = {},
            )
        }

        composeRule.onNodeWithText("Download by").performClick()
        val selectedFlags =
            composeRule.onAllNodesWithText("Surah").fetchSemanticsNodes().map {
                it.config.getOrNull(SemanticsProperties.Selected)
            }

        assertTrue(selectedFlags.any { it == true })
    }

    @Test
    fun aStepperRowStepsAndDisablesAtTheEnds() {
        var steps = 0
        show {
            SettingsStepperRow(
                label = "Arabic",
                valueText = "117%",
                decreaseDescription = "Smaller",
                increaseDescription = "Larger",
                onDecrease = { steps-- },
                onIncrease = { steps++ },
                canDecrease = false,
            )
        }

        composeRule.onNodeWithText("117%").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Smaller").assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Larger")
            .assertWidthIsAtLeast(touchTarget)
            .assertHeightIsAtLeast(touchTarget)
            .performClick()

        assertEquals(1, steps)
    }

    @Test
    fun aLinkRowShowsItsValueAndOpens() {
        var opened = false
        show {
            SettingsLinkRow(label = "Audio", value = "Al-Husary", onClick = { opened = true })
        }

        composeRule.onNodeWithText("Al-Husary").assertIsDisplayed()
        composeRule.onNodeWithText("Audio").performClick()

        assertTrue(opened)
    }

    @Test
    fun theSearchFieldTakesTextAndClearsIt() {
        var query = ""
        show {
            var state by remember { mutableStateOf(query) }
            AwnSearchField(
                query = state,
                onQueryChange = {
                    state = it
                    query = it
                },
                placeholder = "Search settings",
                clearDescription = "Clear search",
            )
        }

        composeRule.onNodeWithContentDescription("Search settings").performTextInput("wifi")
        assertEquals("wifi", query)
        composeRule
            .onNodeWithContentDescription("Clear search")
            .assertWidthIsAtLeast(touchTarget)
            .performClick()

        assertEquals("", query)
    }

    @Test
    fun aDownloadableRowOffersDownloadWithoutATickBox() {
        var downloads = 0
        show {
            AwnPackRow(
                title = "Quantum Foundation",
                detail = "4.2 MB",
                tick = PackTick.None,
                onTickChange = {},
                download = PackRowButton("Download", { downloads++ }),
            )
        }

        composeRule.onNodeWithText("4.2 MB").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Download")
            .assertWidthIsAtLeast(touchTarget)
            .assertHeightIsAtLeast(touchTarget)
            .performClick()

        assertEquals(1, downloads)
        composeRule.onNodeWithContentDescription("Quantum Foundation").assertDoesNotExist()
    }

    @Test
    fun aDownloadingRowShowsProgressAndCancel() {
        var cancelled = false
        show {
            AwnPackRow(
                title = "Abdel Haleem",
                detail = "Downloading, 40%",
                tick = PackTick.None,
                onTickChange = {},
                progress = 0.4f,
                cancel = PackRowButton("Cancel", { cancelled = true }),
                download = PackRowButton("Download", {}),
            )
        }

        composeRule.onNodeWithContentDescription("Download").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cancel").performClick()

        assertTrue(cancelled)
    }

    @Test
    fun aTickedIncludedRowHasATickAndNoButton() {
        var ticked = true
        show {
            AwnPackRow(
                title = "Saheeh International",
                detail = "Included",
                tick = if (ticked) PackTick.On else PackTick.Off,
                onTickChange = { ticked = it },
            )
        }

        composeRule
            .onNodeWithContentDescription("Saheeh International")
            .assertWidthIsAtLeast(touchTarget)
            .assertHeightIsAtLeast(touchTarget)
            .performClick()

        assertFalse(ticked)
        composeRule.onNodeWithContentDescription("Remove").assertDoesNotExist()
    }

    @Test
    fun aDownloadedRowOffersRemove() {
        var removed = false
        show {
            AwnPackRow(
                title = "Quantum Foundation",
                detail = "Downloaded, 4.2 MB",
                tick = PackTick.Off,
                onTickChange = {},
                remove = PackRowButton("Remove", { removed = true }),
            )
        }

        composeRule.onNodeWithContentDescription("Remove").performClick()

        assertTrue(removed)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0} at font scale {1}")
        fun looks(): List<Array<Any>> =
            listOf(
                arrayOf(ColourMode.Awn, 1.0f),
                arrayOf(ColourMode.Awn, 1.3f),
                arrayOf(ColourMode.Black, 1.0f),
                arrayOf(ColourMode.Black, 1.3f),
            )
    }
}
