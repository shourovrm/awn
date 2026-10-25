package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.session.IntervalLabel
import app.awn.quran.feature.learn.session.IntervalUnit
import app.awn.quran.feature.learn.session.RatingChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Drawn 360 dp wide at font scale 2.0, the narrowest and largest case the design system asks for.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RatingButtonsTest {

    @get:Rule val composeRule = createComposeRule()

    private val choices =
        RatingChoices(
            earned = FsrsRating.Good,
            daysByRating =
                mapOf(
                    FsrsRating.Again to 1,
                    FsrsRating.Hard to 4,
                    FsrsRating.Good to 14,
                    FsrsRating.Easy to 400,
                ),
        )

    private fun show(onRate: (FsrsRating) -> Unit, fontScale: Float = 2f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AwnTheme(field = LearnField) {
                    Box(Modifier.width(360.dp)) { RatingButtons(choices, onRate) }
                }
            }
        }
    }

    @Test
    fun everyRatingShowsItsNameAndItsIntervalAtLargeText() {
        show(onRate = {})

        for (text in listOf("Forgot", "Hard", "Good", "Easy", "1 d", "4 d", "2 wk", "1 yr")) {
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun aTapGivesThatRating() {
        val rated = mutableListOf<FsrsRating>()
        show(onRate = { rated += it })

        composeRule.onNodeWithText("Forgot").performClick()
        composeRule.onNodeWithText("Easy").performClick()
        composeRule.onNodeWithText("Good").performClick()

        assertEquals(listOf(FsrsRating.Again, FsrsRating.Easy, FsrsRating.Good), rated)
    }

    @Test
    fun largeTextPutsTheButtonsTwoByTwoAndNormalTextInOneRow() {
        show(onRate = {}, fontScale = 2f)
        val forgot = composeRule.onNodeWithTag("rating_Again").getUnclippedBoundsInRoot()
        val good = composeRule.onNodeWithTag("rating_Good").getUnclippedBoundsInRoot()
        assertTrue("Good is on the second row", good.top > forgot.top)
    }

    @Test
    fun intervalsAreWrittenInTheLargestWholeUnit() {
        assertEquals(IntervalLabel(1, IntervalUnit.Day), IntervalLabel.of(0))
        assertEquals(IntervalLabel(6, IntervalUnit.Day), IntervalLabel.of(6))
        assertEquals(IntervalLabel(2, IntervalUnit.Week), IntervalLabel.of(14))
        assertEquals(IntervalLabel(3, IntervalUnit.Month), IntervalLabel.of(95))
        assertEquals(IntervalLabel(1, IntervalUnit.Year), IntervalLabel.of(400))
    }
}
