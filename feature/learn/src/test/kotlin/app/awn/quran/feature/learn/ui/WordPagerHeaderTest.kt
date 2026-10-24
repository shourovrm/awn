package app.awn.quran.feature.learn.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The word page shows the lesson bar and dots: page count at the top right, one dot per word. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordPagerHeaderTest {

    @get:Rule val composeRule = createComposeRule()

    private val plan = WordPagerPlan(ranks = listOf(21, 22, 23, 24), startIndex = 1)

    private fun showWordPager() {
        composeRule.setContent {
            AwnTheme(field = LearnField) {
                WordPagerScreen(plan = plan, onBack = {}) { rank -> Text("Word $rank") }
            }
        }
    }

    @Test
    fun theBarCountsThePageAndThereIsOneDotPerWord() {
        showWordPager()

        composeRule.onNodeWithText("2 of 4").assertIsDisplayed()
        val dots = composeRule.onAllNodesWithContentDescription("Page", substring = true)
        assertEquals(4, dots.fetchSemanticsNodes().size)
    }

    @Test
    fun tappingADotJumpsToThatWord() {
        showWordPager()

        composeRule.onNodeWithContentDescription("Page 4").performClick()

        composeRule.onNodeWithText("4 of 4").assertIsDisplayed()
        composeRule.onNodeWithText("Word 24").assertIsDisplayed()
    }
}
