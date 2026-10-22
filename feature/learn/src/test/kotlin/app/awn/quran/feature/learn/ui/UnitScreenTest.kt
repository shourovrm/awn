package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.PathState
import app.awn.quran.feature.learn.course.TrackUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnitScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val lesson: CourseLesson =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"letters-01",
            "course":"letters","order":1,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"All 29 letters","screens":[
            {"kind":"screen","title":"The Arabic letters","blocks":[
              {"type":"paragraph","text":"Arabic has 29 letters."}]},
            {"kind":"check","title":"C","check":{"question":"Which side?",
              "options":["Left","Right"],"answer":1}},
            {"kind":"answer","title":"The right edge","blocks":[]}]}}}]}""",
            )
            .lessons
            .single()

    private val next = TrackUnit("letters-02", 2, "Letter shapes", PathState.Next)

    @Test
    fun aUnitShowsOnePageAtATimeAndCountsTheRightAnswers() {
        val finished = mutableListOf<Int>()
        val opened = mutableListOf<TrackUnit>()

        composeRule.setContent {
            AwnTheme(field = LearnField) {
                UnitScreen(
                    lesson = lesson,
                    languageCode = "en",
                    place = UnitPlace(LearnTrack.Reading, number = 1, next = next),
                    actions =
                        UnitActions(
                            onBack = {},
                            onFinished = { finished += it },
                            onNextUnit = { opened += it },
                        ),
                )
            }
        }
        composeRule.onNodeWithText("The Arabic letters").assertIsDisplayed()
        composeRule.onNodeWithText("1 of 3").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Page 2").performClick()
        composeRule.onNodeWithText("Right").performClick()
        composeRule.onNodeWithText("The right edge").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Page 3").performClick()
        composeRule.onNodeWithText("Finished unit 1").assertIsDisplayed()
        composeRule.onNodeWithText("Right: 1 of 1").assertIsDisplayed()
        composeRule.onNodeWithTag(UNIT_NEXT_TAG).performClick()

        assertEquals(listOf(1), finished)
        assertEquals(listOf(next), opened)
    }
}
