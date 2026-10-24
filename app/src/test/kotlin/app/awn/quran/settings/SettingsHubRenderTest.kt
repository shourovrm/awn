package app.awn.quran.settings

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Renders the settings hub in Awn and Black modes at font scales 1.0 and 1.3. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp", application = Application::class)
class SettingsHubRenderTest(private val mode: ColourMode, private val fontScale: Float) {

    @get:Rule val composeRule = createComposeRule()

    init {
        // The app's own manifest does not declare the activity that Compose tests launch.
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private val summaries =
        mapOf(
            SettingsSection.Mushaf to "Tajweed colours · page numbers shown",
            SettingsSection.Audio to "Al-Husary · Wi-Fi only",
            SettingsSection.Downloads to "788 kB used",
        )

    private var chosenLanguage: AppLanguage? = null
    private var openedRoute: String? = null
    private var openedRow: String? = null

    private fun showHub() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                SettingsHubScreen(
                    summaries = summaries,
                    onBack = {},
                    language = AppLanguage.English,
                    onLanguageChange = { chosenLanguage = it },
                    onOpenSection = {},
                    onOpenRow = { route, rowKey ->
                        openedRoute = route
                        openedRow = rowKey
                    },
                )
            }
        }
    }

    @Test
    fun theHubGroupsSectionsAndShowsTheCurrentValueUnderEach() {
        showHub()

        composeRule.onNodeWithText("Listening").assertIsDisplayed()
        composeRule.onNodeWithText("Al-Husary · Wi-Fi only").assertIsDisplayed()
        composeRule.onNodeWithText("Mushaf").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithText("Search settings").assertIsDisplayed()
    }

    @Test
    fun typingFindsASettingOnAnotherPageAndOpensItsRow() {
        showHub()

        composeRule.onNodeWithContentDescription("Search settings").performTextInput("wi-fi")
        composeRule.onNodeWithText("Wi-Fi only").assertIsDisplayed().performClick()

        assertEquals("settings/audio", openedRoute)
        assertEquals("audio.wifi_only", openedRow)
    }

    @Test
    fun aBanglaQueryFindsASettingWhileTheAppIsInEnglish() {
        showHub()

        composeRule.onNodeWithContentDescription("Search settings").performTextInput("ওয়াই-ফাই")
        composeRule.onNodeWithText("Wi-Fi only").assertIsDisplayed()
    }

    @Test
    fun theHubHasALanguageRowThatShowsTheCurrentLanguageAndOpensTheChoice() {
        showHub()

        composeRule.onNodeWithText("Language").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("English").assertIsDisplayed()
        composeRule.onNodeWithText("Language").performClick()
        composeRule.onNodeWithText("বাংলা").performClick()

        assertEquals(AppLanguage.Bangla, chosenLanguage)
    }

    @Test
    fun searchFindsLanguageInEnglishAndInBangla() {
        showHub()

        composeRule.onNodeWithContentDescription("Search settings").performTextInput("ভাষা")

        composeRule.onNodeWithText("Language").assertIsDisplayed()
    }

    @Test
    fun aQueryWithNoMatchSaysSo() {
        showHub()

        composeRule.onNodeWithContentDescription("Search settings").performTextInput("zzzz")

        composeRule.onNodeWithText("No settings found").assertIsDisplayed()
        assertTrue(openedRoute == null)
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
