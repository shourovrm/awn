package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.PathState
import app.awn.quran.feature.learn.course.TrackSummary
import app.awn.quran.feature.learn.course.TrackUnit
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
            onTrack = {},
            onReadingProgress = {},
            onReviseAyahs = {},
            onSessionMinutes = {},
            onSources = {},
            onDownloadMeanings = {},
            onLesson = {},
            onChangeChoices = {},
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
            tracks = emptyList(),
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
    fun theHubListsTheTracksAndOpensOne() {
        val opened = mutableListOf<LearnTrack>()
        val reading =
            TrackSummary(
                track = LearnTrack.Reading,
                doneCount = 1,
                unitCount = 8,
                next = TrackUnit("letters-02", 2, "Letter shapes", PathState.Next),
                opensAfterReadingUnit = null,
            )
        val tajweed = TrackSummary(LearnTrack.Tajweed, 0, 12, null, opensAfterReadingUnit = 7)
        val plan = TodayPlan(steps = emptyList(), plannedSeconds = 0)

        composeRule.setContent {
            AwnTheme(field = LearnField) {
                CourseHub(
                    hubState(plan).copy(tracks = listOf(reading, tajweed)),
                    noHubActions.withTrack { opened += it },
                )
            }
        }
        composeRule.onNodeWithText("Unit 2 of 8 · Letter shapes").assertIsDisplayed()
        composeRule.onNodeWithText("Best after Reading unit 7").assertIsDisplayed()
        composeRule.onNodeWithText("Reading").performClick()

        assertEquals(listOf(LearnTrack.Reading), opened)
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

    private fun HubActions.withTrack(onTrack: (LearnTrack) -> Unit) =
        HubActions(
            onStart = onStart,
            onProgress = onProgress,
            onWordList = onWordList,
            onQuiz = onQuiz,
            onTrack = onTrack,
            onReadingProgress = onReadingProgress,
            onReviseAyahs = onReviseAyahs,
            onSessionMinutes = onSessionMinutes,
            onSources = onSources,
            onDownloadMeanings = onDownloadMeanings,
            onLesson = onLesson,
            onChangeChoices = onChangeChoices,
        )

    private fun HubActions.withStart(onStart: () -> Unit) =
        HubActions(
            onStart = onStart,
            onProgress = onProgress,
            onWordList = onWordList,
            onQuiz = onQuiz,
            onTrack = onTrack,
            onReadingProgress = onReadingProgress,
            onReviseAyahs = onReviseAyahs,
            onSessionMinutes = onSessionMinutes,
            onSources = onSources,
            onDownloadMeanings = onDownloadMeanings,
            onLesson = onLesson,
            onChangeChoices = onChangeChoices,
        )
}
