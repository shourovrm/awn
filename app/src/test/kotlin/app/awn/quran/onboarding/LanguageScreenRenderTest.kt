package app.awn.quran.onboarding

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Renders the language screen in Awn and Black modes at font scales 1.0 and 1.3. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp", application = Application::class)
class LanguageScreenRenderTest(
    private val mode: ColourMode,
    private val fontScale: Float,
    private val startLanguage: AppLanguage,
) {

    @get:Rule val composeRule = createComposeRule()

    init {
        // The app's own manifest does not declare the activity that Compose tests launch.
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private var continuedCount = 0
    private var highlighted by mutableStateOf(startLanguage)

    private val englishTitle = "Choose your language"
    private val banglaTitle = "ভাষা বেছে নিন"
    private val englishContinue = "Continue"
    private val banglaContinue = "চালিয়ে যান"

    private val startsInBangla = startLanguage == AppLanguage.Bangla
    private val startTitle = if (startsInBangla) banglaTitle else englishTitle
    private val startContinue = if (startsInBangla) banglaContinue else englishContinue
    private val startOwnName = if (startsInBangla) "বাংলা" else "English"
    private val otherOwnName = if (startsInBangla) "English" else "বাংলা"
    private val otherTitle = if (startsInBangla) englishTitle else banglaTitle

    private fun block(ownName: String): SemanticsMatcher = hasText(ownName) and isSelectable()

    private fun showScreen() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                LanguageScreen(
                    selected = highlighted,
                    onSelect = { highlighted = it },
                    onContinue = { continuedCount++ },
                )
            }
        }
    }

    @Test
    fun theScreenIsWrittenInTheHighlightedLanguage() {
        showScreen()

        composeRule.onNodeWithText(startTitle).assertExists()
        composeRule.onNodeWithText(startContinue).performScrollTo().assertExists()
    }

    @Test
    fun theHighlightedBlockIsSelectedAndTheOtherIsNot() {
        showScreen()

        composeRule.onNode(block(startOwnName)).assertIsSelected()
        composeRule.onNode(block(otherOwnName)).assertIsNotSelected()
    }

    @Test
    fun bothLanguageBlocksAreAtLeast96DpTall() {
        showScreen()

        composeRule.onNode(block("English")).assertHeightIsAtLeast(96.dp)
        composeRule.onNode(block("বাংলা")).assertHeightIsAtLeast(96.dp)
    }

    @Test
    fun theContinueButtonMeetsTheTouchTargetAndReportsTheTap() {
        showScreen()

        composeRule.onNodeWithText(startContinue).performScrollTo().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(startContinue).performClick()

        assertEquals(1, continuedCount)
    }

    @Test
    fun choosingTheOtherBlockSwitchesTheScreenTextAtOnce() {
        showScreen()

        composeRule.onNode(block(otherOwnName)).performClick()

        composeRule.onNodeWithText(otherTitle).assertExists()
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0} at font scale {1} starting in {2}")
        fun looks(): List<Array<Any>> =
            listOf(ColourMode.Awn, ColourMode.Black).flatMap { mode ->
                listOf(1.0f, 1.3f).flatMap { scale ->
                    AppLanguage.entries.map { language -> arrayOf<Any>(mode, scale, language) }
                }
            }
    }
}
