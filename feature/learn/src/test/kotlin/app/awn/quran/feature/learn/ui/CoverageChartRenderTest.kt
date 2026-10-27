package app.awn.quran.feature.learn.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.words.CoverageMilestone
import java.text.NumberFormat
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Compose tests run on Android 15: Espresso calls an InputManager method that Android 16 removed.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverageChartRenderTest {

    @get:Rule val composeRule = createComposeRule()

    private val milestones =
        listOf(100, 200, 300, 500, 1_000, 2_000, 5_000).map { CoverageMilestone(it, share = 0.5) }

    private val progress =
        CourseProgress(
            wordsMet = 0,
            wordsKnown = 0,
            coverage = 0.5,
            milestones = milestones,
        )

    @Test
    fun everyCoverageColumnShowsItsWordCountAndTheUserColumn() {
        composeRule.setContent {
            AwnTheme(field = LearnField) { CourseProgressScreen(progress = progress, onBack = {}) }
        }

        milestones.forEach { milestone ->
            val label = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(milestone.wordCount)
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("You").assertExists()
    }

    @Test
    fun coverageLabelsUseTheAppLanguageDigitsNotTheDeviceLocale() {
        val banglaConfiguration =
            Configuration(
                ApplicationProvider.getApplicationContext<Context>().resources.configuration,
            )
        banglaConfiguration.setLocale(Locale.forLanguageTag("bn"))
        val banglaLabel = NumberFormat.getIntegerInstance(Locale.forLanguageTag("bn")).format(1_000)
        // Without this the check would pass on a JDK that prints Bangla with Latin digits.
        assertNotEquals("1,000", banglaLabel)

        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides banglaConfiguration) {
                AwnTheme(field = LearnField) {
                    CourseProgressScreen(progress = progress, onBack = {})
                }
            }
        }

        composeRule.onNodeWithText(banglaLabel).assertExists()
    }
}
