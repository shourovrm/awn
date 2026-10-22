package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.session.FrameLabel
import app.awn.quran.feature.learn.session.ItemRef
import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.session.SessionFrame
import app.awn.quran.feature.learn.session.SessionState
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.session.WordInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Compose tests run on Android 15: Espresso calls an InputManager method that Android 16 removed.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CourseRenderTest {

    @get:Rule val composeRule = createComposeRule()

    private val noHubActions =
        HubActions(
            onStart = {},
            onProgress = {},
            onWordList = {},
            onQuiz = {},
            onTajweed = {},
            onReadingProgress = {},
            onReviseAyahs = {},
            onSessionMinutes = {},
            onSources = {},
            onDownloadMeanings = {},
            onLesson = {},
        )

    private fun hubState(plan: TodayPlan) =
        HubState(
            plan = plan,
            sessionMinutes = 10,
            path = emptyList(),
            lessonTitles = emptyMap(),
            triggerMeanings = emptyMap(),
            wordsMet = 0,
            wordsKnown = 0,
            coverage = 0.0,
            dueAyahs = 0,
            meanings = MeaningsState.Ready,
        )

    @Test
    fun theHubShowsTodayAndStartsTheDay() {
        var starts = 0
        val plan =
            TodayPlan(
                steps =
                    List(10) { PlanStep.NewWord(rank = it + 1) } +
                        List(3) { PlanStep.Review(ItemRef.Word(rank = 100 + it)) },
                plannedSeconds = 600,
            )

        composeRule.setContent {
            AwnTheme(field = LearnField) {
                CourseHub(hubState(plan), noHubActions.withStart { starts += 1 })
            }
        }
        composeRule.onNodeWithText("Today · 10 min").assertIsDisplayed()
        composeRule.onNodeWithText("Start").performClick()

        assertEquals(1, starts)
    }

    @Test
    fun aNewWordFrameShowsItsMeaningAndAdvances() {
        var nexts = 0
        val info =
            WordInfo(
                rank = 1,
                representative = WordKey(1, 1, 2),
                meaning = "the name",
                letterCount = 4,
                ayahWordCount = 4,
            )
        val state =
            SessionState(
                frame = SessionFrame.NewWord(info, emptyList(), FrameLabel.NewWord(1, 10)),
                progress = 0f,
                secondsLeft = 600,
            )

        composeRule.setContent {
            AwnTheme(field = LearnField) {
                SessionScreen(
                    state = state,
                    actions =
                        SessionActions(
                            onClose = {},
                            onNext = { nexts += 1 },
                            onAnswer = {},
                            onDone = {},
                        ),
                )
            }
        }
        composeRule.onNodeWithText("the name").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()

        assertEquals(1, nexts)
    }

    private fun HubActions.withStart(onStart: () -> Unit) =
        HubActions(
            onStart = onStart,
            onProgress = onProgress,
            onWordList = onWordList,
            onQuiz = onQuiz,
            onTajweed = onTajweed,
            onReadingProgress = onReadingProgress,
            onReviseAyahs = onReviseAyahs,
            onSessionMinutes = onSessionMinutes,
            onSources = onSources,
            onDownloadMeanings = onDownloadMeanings,
            onLesson = onLesson,
        )
}
