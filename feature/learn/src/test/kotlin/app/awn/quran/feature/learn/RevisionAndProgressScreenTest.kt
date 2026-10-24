package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Every screen is drawn 360 dp wide at font scale 2.0, the narrowest and largest case the design
// system asks for.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RevisionAndProgressScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val revision =
        RevisionUiState(
            current = RevisionCard(AyahRef(1, 2), "Al-Fatihah 1:2", "الْحَمْدُ لِلَّهِ"),
            remaining = 3,
            total = 5,
            isTextShown = true,
        )

    private fun showNarrowAndLarge(mode: ColourMode, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                Box(Modifier.width(360.dp)) { content() }
            }
        }
    }

    @Test
    fun theFourGradesAreShownAndMapToFsrsRatings() {
        val rated = mutableListOf<FsrsRating>()
        showNarrowAndLarge(ColourMode.Black) {
            RevisionScreen(
                revision,
                onShowTextClick = {},
                onRate = { rated += it },
                onBackClick = {},
            )
        }

        composeRule.onNodeWithText("Forgot").performClick()
        composeRule.onNodeWithText("Hard").performClick()
        composeRule.onNodeWithText("Good").performClick()
        composeRule.onNodeWithText("Easy").performClick()

        assertEquals(
            listOf(FsrsRating.Again, FsrsRating.Hard, FsrsRating.Good, FsrsRating.Easy),
            rated,
        )
    }

    @Test
    fun gradeButtonsAreAtLeast48DpTallAtLargeText() {
        showNarrowAndLarge(ColourMode.Light) {
            RevisionScreen(revision, onShowTextClick = {}, onRate = {}, onBackClick = {})
        }

        composeRule.onNodeWithText("Forgot").assertHeightIsAtLeast(AwnSpacing.touchTarget)
    }

    @Test
    fun revisionBarShowsTitleAndBackButton() {
        showNarrowAndLarge(ColourMode.Dark) {
            RevisionScreen(revision, onShowTextClick = {}, onRate = {}, onBackClick = {})
        }

        composeRule.onNodeWithText("Revise").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
