package app.awn.quran.feature.learn.ui

import android.content.Context
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.course.LevelSection
import app.awn.quran.feature.learn.course.LevelState
import app.awn.quran.feature.learn.course.PathRow
import app.awn.quran.feature.learn.course.PathState
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.words.WordLevels
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Compose tests run on Android 15: Espresso calls an InputManager method that Android 16 removed.
@RunWith(RobolectricTestRunner::class)
// A tall window so the whole path shows without scrolling.
@Config(sdk = [35], qualifiers = "w400dp-h1600dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordsPathRenderTest {

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

    private val pathRows =
        listOf(
            PathRow.SetStep(1, metCount = 10, knownCount = 10, PathState.Done, isToday = false),
            PathRow.SetStep(2, metCount = 10, knownCount = 9, PathState.Done, isToday = false),
            PathRow.LessonStep("grammar-03", triggerRank = 12, 2, PathState.Done, isToday = false),
            PathRow.SetStep(3, metCount = 0, knownCount = 0, PathState.Next, isToday = true),
            PathRow.SetStep(4, metCount = 0, knownCount = 0, PathState.Upcoming, isToday = false),
        )

    private val state =
        WordsTrackState(
            hub =
                HubState(
                    plan = TodayPlan(steps = emptyList(), plannedSeconds = 0),
                    sessionMinutes = 10,
                    path = emptyList(),
                    lessonTitles = mapOf("grammar-03" to "Nouns"),
                    triggerMeanings = emptyMap(),
                    wordsMet = 20,
                    wordsKnown = 19,
                    coverage = 0.1,
                    dueAyahs = 0,
                    meanings = MeaningsState.Ready,
                    tracks = emptyList(),
                ),
            todaySet = 3,
            todayWords = emptyList(),
            pageLinks = emptyList(),
            levels = listOf(LevelSection(WordLevels.all.first(), pathRows, LevelState.Current)),
            previewKeys = emptyMap<Int, List<WordKey>>(),
        )

    @Test
    fun theLineRunsThroughTheGrammarLineAndTodaysDotSitsOutsideTheCard() {
        val density =
            Density(
                ApplicationProvider.getApplicationContext<Context>()
                    .resources
                    .displayMetrics
                    .density,
            )
        composeRule.setContent {
            AwnTheme(field = LearnField) {
                WordsTrackScreen(
                    state,
                    WordsTrackActions(noHubActions, onOpenSet = {}, onOpenPageWords = {}),
                    onBack = {},
                )
            }
        }
        // Once in the Today card and once on the path.
        composeRule.onAllNodesWithText("Set 3").assertCountEquals(2)
        val grammar = composeRule.onNodeWithText("Grammar · Nouns, after set 2")
        grammar.assertIsDisplayed()

        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val lineX =
            with(density) { (AwnSpacing.screenMargin + AwnSpacing.pathDotColumn / 2).roundToPx() }
        val gutterX = with(density) { (AwnSpacing.screenMargin + 4.dp).roundToPx() }
        val bounds = grammar.getBoundsInRoot()
        val centreY = with(density) { ((bounds.top + bounds.bottom) / 2).roundToPx() }

        // The line colour differs from the empty background beside it.
        assertNotEquals(image[gutterX, centreY], image[lineX, centreY])
    }
}
