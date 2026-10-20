package app.awn.quran.feature.learn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Espresso, which Compose tests use to wait for idle, calls an InputManager method that Android 16
// removed, so this test runs on Android 15.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearnScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val state =
        LearnUiState(
            streakDays = 5,
            pagesThisWeek = 14,
            ayahsDueToday = 3,
            lessons =
                listOf(
                    LessonSummary("noon-sakinah-tanween", "Noon sakinah and tanween", 4, false),
                    LessonSummary("qalqalah", "Qalqalah", 3, true),
                ),
            snippet =
                SnippetLine(
                    "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ",
                    "Sahih al-Bukhari 5027",
                ),
        )

    private fun show(
        state: LearnUiState,
        onRevise: () -> Unit = {},
        onLesson: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            LearnScreen(
                state = state,
                onLessonClick = onLesson,
                onAllLessonsClick = {},
                onProgressClick = {},
                onReviseClick = onRevise,
            )
        }
    }

    @Test
    fun showsFiguresLessonsSnippetAndReviseButton() {
        show(state)

        composeRule.onNodeWithText("14").assertIsDisplayed()
        composeRule.onNodeWithText("pages read this week").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("days in a row").assertIsDisplayed()
        composeRule.onNodeWithText("Qalqalah").assertIsDisplayed()
        composeRule.onNodeWithText("3 examples · Draft").assertIsDisplayed()
        composeRule.onNodeWithText("Sahih al-Bukhari 5027").assertExists()
        composeRule.onNodeWithText("Revise 3 ayahs").assertIsDisplayed()
    }

    @Test
    fun reviseButtonStartsRevision() {
        var revised = false
        show(state, onRevise = { revised = true })

        composeRule.onNodeWithText("Revise 3 ayahs").performClick()

        assertEquals(true, revised)
    }

    @Test
    fun lessonLineOpensItsLesson() {
        var opened = ""
        show(state, onLesson = { opened = it })

        composeRule.onNodeWithText("Qalqalah").performClick()

        assertEquals("qalqalah", opened)
    }

    @Test
    fun reviseButtonIsDisabledWhenNothingIsDue() {
        show(state.copy(ayahsDueToday = 0))

        composeRule.onNodeWithText("Nothing to revise").assertIsNotEnabled()
    }
}
