package app.awn.quran.feature.read.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.core.ui.components.PackTick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

private val touchTarget = 48.dp

/** Every state of a download list entry, in Awn and Black modes at font scales 1.0 and 1.3. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class PackListRenderTest(private val mode: ColourMode, private val fontScale: Float) {

    @get:Rule val composeRule = createComposeRule()

    private val noActions = CatalogueRowActions({}, {}, {})

    private fun item(
        id: String,
        name: String,
        installed: Boolean = false,
        bundled: Boolean = false,
    ) =
        fakeCatalogueItem(
                id,
                group = CatalogueGroup.Translation,
                language = "bn",
                isInstalled = installed,
                isBundled = bundled,
                downloadBytes = 4_200_000L,
                installedBytes = if (installed) 4_200_000L else null,
            )
            .copy(name = name)

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
    fun anIncludedTickedEntryShowsIncludedAndOffersNoRemove() {
        show {
            CatalogueEntryRow(
                row = CatalogueRow(item("bn_zakaria", "Abu Bakr Zakaria", true, true), null),
                actions = noActions,
                tick = PackTick.On,
                onTickChange = {},
            )
        }

        composeRule.onNodeWithText("Included").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Abu Bakr Zakaria")
            .assertWidthIsAtLeast(touchTarget)
            .assertHeightIsAtLeast(touchTarget)
        composeRule.onNodeWithContentDescription("Remove").assertDoesNotExist()
    }

    @Test
    fun aDownloadableEntryOffersDownloadAndShowsItsSize() {
        var installs = 0
        show {
            CatalogueEntryRow(
                row = CatalogueRow(item("en_haleem", "Abdel Haleem"), null),
                actions = CatalogueRowActions({ installs++ }, {}, {}),
                tick = PackTick.Off,
                onTickChange = {},
            )
        }

        composeRule.onNodeWithContentDescription("Abdel Haleem").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription("Download")
            .assertWidthIsAtLeast(touchTarget)
            .assertHeightIsAtLeast(touchTarget)
            .performClick()

        assertEquals(1, installs)
    }

    @Test
    fun aDownloadingEntryShowsProgressAndCancel() {
        var cancels = 0
        show {
            CatalogueEntryRow(
                row = CatalogueRow(item("en_haleem", "Abdel Haleem"), PackTransfer.Running(0.4f)),
                actions = CatalogueRowActions({}, { cancels++ }, {}),
                tick = PackTick.Off,
                onTickChange = {},
            )
        }

        composeRule.onNodeWithText("Downloading 40%").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Download").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cancel download").performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun removingADownloadedEntryAsksFirstNamingTheItemAndTheSpaceFreed() {
        var removals = 0
        show {
            CatalogueEntryRow(
                row = CatalogueRow(item("en_haleem", "Abdel Haleem", installed = true), null),
                actions = CatalogueRowActions({}, {}, { removals++ }),
                tick = PackTick.Off,
                onTickChange = {},
            )
        }

        composeRule.onNodeWithContentDescription("Remove").performClick()
        composeRule.onNodeWithText("Remove Abdel Haleem?").assertIsDisplayed()
        composeRule.onNodeWithText("Frees 4.2 MB. You can download it again.").assertIsDisplayed()
        assertEquals(0, removals)
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(1, removals)
    }

    @Test
    fun theQuantumTranslationShowsTheMeaningBasedNoteAndZakariaDoesNot() {
        show {
            Column {
                CatalogueEntryRow(
                    row = CatalogueRow(item("bn_quantum", "Quantum Foundation", true), null),
                    actions = noActions,
                    tick = PackTick.On,
                    onTickChange = {},
                )
                CatalogueEntryRow(
                    row = CatalogueRow(item("bn_zakaria", "Abu Bakr Zakaria", true, true), null),
                    actions = noActions,
                    tick = PackTick.On,
                    onTickChange = {},
                )
            }
        }

        composeRule.onNodeWithText("Quantum Foundation (meaning-based)").assertIsDisplayed()
        composeRule.onNodeWithText("Abu Bakr Zakaria").assertIsDisplayed()
        composeRule.onNodeWithText("Abu Bakr Zakaria (meaning-based)").assertDoesNotExist()
    }

    @Test
    fun twoWordByWordEntriesShowOneTickWhateverTheLayerFlagIs() {
        val shown = wordLanguageShown(chosen = null, appLanguage = "bn", setOf("en", "bn"))
        show {
            Column {
                listOf("en" to "English", "bn" to "Bangla").forEach { (language, name) ->
                    CatalogueEntryRow(
                        row =
                            CatalogueRow(
                                item("wbw_$language", name, installed = true)
                                    .copy(language = language),
                                null,
                            ),
                        actions = noActions,
                        tick = if (language == shown) PackTick.On else PackTick.Off,
                        onTickChange = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("English").assertIsOff()
        composeRule.onNodeWithContentDescription("Bangla").assertIsOn()
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
