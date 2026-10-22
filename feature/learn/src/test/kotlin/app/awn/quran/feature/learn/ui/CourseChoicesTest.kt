package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user (2026-10-22): "In learning, one cannot change his learning style even if he wanted; so
 * if someone pressed he does not know the arabic letters, he can't go back; this option is
 * available to change style in hifz." The first-open choices (Arabic letters, time each day) can
 * now be changed from the hub: the same screen opens with the current choices ticked, and Back
 * returns to the hub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CourseChoicesTest {

    @get:Rule val composeRule = createComposeRule()

    private var saved: FirstChoices? = null

    private fun showChoices(current: FirstChoices) {
        composeRule.setContent {
            AwnTheme { FirstOpenScreen(initial = current, onContinue = { saved = it }) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theScreenOpensWithTheCurrentChoicesTicked() {
        showChoices(FirstChoices(includeLetters = true, minutes = 15))

        composeRule.onNodeWithText("Not yet").assertIsSelected()
        composeRule.onNodeWithText("I can read them").assertIsNotSelected()
    }

    @Test
    fun someoneWhoChoseLetterLessonsCanSwitchToWords() {
        showChoices(FirstChoices(includeLetters = true, minutes = 15))

        composeRule.onNodeWithText("I can read them").performClick()
        composeRule.onNodeWithText("Continue").performClick()

        assertEquals(FirstChoices(includeLetters = false, minutes = 15), saved)
    }

    @Test
    fun someoneWhoChoseWordsCanSwitchToLetterLessons() {
        showChoices(FirstChoices(includeLetters = false, minutes = 10))

        composeRule.onNodeWithText("Not yet").performClick()
        composeRule.onNodeWithText("Continue").performClick()

        assertEquals(FirstChoices(includeLetters = true, minutes = 10), saved)
    }

    @Test
    fun backFromTheChoicesReturnsToTheHub() {
        assertEquals(LearnRoute.Hub, learnBackFrom(LearnRoute.CourseChoices))
    }
}
