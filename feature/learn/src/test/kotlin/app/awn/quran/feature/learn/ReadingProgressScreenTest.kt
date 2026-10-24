package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Drawn 360 dp wide at font scale 2.0, the narrowest and largest case the design system asks for.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingProgressScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val weekdays = listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon")

    private fun week(pagesEachDay: List<Int>, activeFrom: Int) =
        weekdays.mapIndexed { index, label ->
            WeekDay(
                label,
                isActive = index >= activeFrom,
                isToday = index == 6,
                pages = pagesEachDay[index],
            )
        }

    private val activeReader =
        ProgressUiState(
            streakDays = 2,
            days = week(listOf(0, 0, 0, 0, 0, 9, 4), activeFrom = 5),
            pagesThisWeek = 13,
            memorisedCount = 28,
            wordsKnown = 31,
            quranWordsPercent = 12,
            nextRevisionDay = "Tuesday",
            nextRevisionCount = 3,
        )

    private val newUser =
        ProgressUiState(
            streakDays = 0,
            days = week(List(7) { 0 }, activeFrom = 7),
            pagesThisWeek = 0,
            memorisedCount = 0,
            wordsKnown = 0,
            quranWordsPercent = 0,
            nextRevisionDay = null,
            nextRevisionCount = 0,
        )

    private fun show(state: ProgressUiState, actions: ReadingProgressActions) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                AwnTheme(field = AwnField.Paper) {
                    Box(Modifier.width(360.dp)) {
                        ReadingProgressScreen(state, actions, onBackClick = {})
                    }
                }
            }
        }
    }

    private fun noActions() =
        ReadingProgressActions(onNextRevisionClick = {}, read = null, hasReadBefore = false)

    @Test
    fun anActiveReaderSeesTheStreakFourTilesAndTheChart() {
        show(activeReader, noActions())

        composeRule.onNodeWithText("Progress").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("days in a row").assertExists()
        composeRule.onNodeWithText("13").assertExists()
        composeRule.onNodeWithText("pages this week").assertExists()
        composeRule.onNodeWithText("28").assertExists()
        composeRule.onNodeWithText("ayahs memorised").assertExists()
        composeRule.onNodeWithText("31").assertExists()
        composeRule.onNodeWithText("words known").assertExists()
        composeRule.onNodeWithText("12%").assertExists()
        composeRule.onNodeWithText("of Quran words").assertExists()
        composeRule.onNodeWithText("Pages this week").assertExists()
        composeRule.onNodeWithText("Open a page to start your count").assertDoesNotExist()
    }

    @Test
    fun theNextRevisionRowOpensTheReviseScreen() {
        var openedRevise = 0
        show(
            activeReader,
            ReadingProgressActions({ openedRevise += 1 }, read = null, hasReadBefore = true),
        )

        composeRule.onNodeWithText("Next revision").performScrollTo().performClick()

        assertEquals(1, openedRevise)
        composeRule.onNodeWithText("Tuesday, 3 ayahs").assertExists()
    }

    @Test
    fun aNewUserSeesZerosAndAWayToStartReading() {
        var openedPage = 0
        val read = OpenPageAction(1) { openedPage = 1 }
        show(newUser, ReadingProgressActions({}, read, hasReadBefore = false))

        composeRule.onNodeWithText("0%").assertExists()
        composeRule.onNodeWithText("Open a page to start your count").assertExists()
        composeRule.onNodeWithText("Start reading").performScrollTo().performClick()

        assertEquals(1, openedPage)
        composeRule.onNodeWithText("Next revision").assertDoesNotExist()
    }

    @Test
    fun aNewUserWhoOpenedAPageBeforeSeesContinueReading() {
        show(newUser, ReadingProgressActions({}, OpenPageAction(77) {}, hasReadBefore = true))

        composeRule.onNodeWithText("Continue reading").assertExists()
    }
}
