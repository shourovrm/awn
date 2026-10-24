package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.session.TodayPlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hub has a row that opens the course choices, as the Hifz tab has one for its method. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubChoicesEntryTest {

    @get:Rule val composeRule = createComposeRule()

    private fun showHub(onChangeChoices: () -> Unit = {}) {
        val actions =
            HubActions(
                onStart = {},
                onProgress = {},
                onWordList = {},
                onQuiz = {},
                onTrack = {},
                onReadingProgress = {},
                onReviseAyahs = {},
                onSessionMinutes = {},
                onSources = {},
                onDownloadMeanings = {},
                onLesson = {},
                onChangeChoices = onChangeChoices,
            )
        val state =
            HubState(
                plan = TodayPlan(emptyList(), 0),
                sessionMinutes = 10,
                path = emptyList(),
                lessonTitles = emptyMap(),
                triggerMeanings = emptyMap(),
                wordsMet = 0,
                wordsKnown = 0,
                coverage = 0.0,
                dueAyahs = 0,
                meanings = MeaningsState.Ready,
                tracks = emptyList(),
            )
        composeRule.setContent { AwnTheme { CourseHub(state, actions) } }
        composeRule.waitForIdle()
    }

    @Test
    fun theHubOffersTheLessonLanguage() {
        showHub()

        composeRule.onNodeWithText("Lesson language").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theHubOpensTheCourseChoices() {
        var opened = 0
        showHub(onChangeChoices = { opened += 1 })

        composeRule.onNodeWithText("Letters and daily time").performScrollTo().performClick()

        assertEquals(1, opened)
    }
}
