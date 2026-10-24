package app.awn.quran.feature.learn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class ReviseAyahsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val weekdays = listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon")
    private val upcomingCounts = listOf(3, 0, 5, 2, 0, 6, 1)

    private val someDue =
        ReviseUiState(
            dueCount = 9,
            dueSurahs =
                listOf(
                    DueSurah("Al-Fatihah", 2, 5, 4),
                    DueSurah("Al-Ikhlas", 1, 3, 3),
                    DueSurah("Al-Falaq", 2, 2, 1),
                ),
            upcomingDays = weekdays.zip(upcomingCounts).map { UpcomingDay(it.first, it.second) },
            nextRevisionDay = "Tuesday",
            nextRevisionCount = 3,
        )

    private val nothingDue = someDue.copy(dueCount = 0, dueSurahs = emptyList())

    private fun show(
        state: ReviseUiState,
        actions: ReviseActions = ReviseActions({}, {}, memorise = null),
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                AwnTheme(field = AwnField.Paper) {
                    Box(Modifier.width(360.dp)) {
                        ReviseAyahsScreen(state, actions, onBackClick = {})
                    }
                }
            }
        }
    }

    @Test
    fun withAyahsDueTheCountSurahsAndStartAreShown() {
        var startCount = 0
        show(someDue, ReviseActions({ startCount += 1 }, {}, memorise = null))

        composeRule.onNodeWithText("9").assertIsDisplayed()
        composeRule.onNodeWithText("ayahs due today").assertIsDisplayed()
        composeRule.onNodeWithText("Al-Fatihah").assertExists()
        composeRule.onNodeWithText("Ayahs 2 to 5").assertExists()
        composeRule.onNodeWithText("Ayah 2").assertExists()
        composeRule.onNodeWithText("Coming up").assertExists()
        composeRule.onNodeWithText("Start").performClick()

        assertEquals(1, startCount)
    }

    @Test
    fun withNothingDueTheNextRevisionAndTheEarlyActionAreShown() {
        var earlyCount = 0
        show(nothingDue, ReviseActions({}, { earlyCount += 1 }, memorise = null))

        composeRule.onNodeWithText("Nothing due today").assertIsDisplayed()
        composeRule.onNodeWithText("Next revision is Tuesday, 3 ayahs.").assertExists()
        composeRule.onNodeWithText("Revise early").performClick()

        assertEquals(1, earlyCount)
    }

    @Test
    fun theMemoriseRowOpensTheHifzPage() {
        var openedPage = 0
        show(nothingDue, ReviseActions({}, {}, memorise = OpenPageAction(42) { openedPage = 42 }))

        composeRule.onNodeWithText("Memorise a new ayah").performClick()

        assertEquals(42, openedPage)
    }

    @Test
    fun withoutHifzPageOrNextRevisionNoMeanwhileRowsAreShown() {
        val empty =
            nothingDue.copy(
                nextRevisionDay = null,
                nextRevisionCount = 0,
                upcomingDays = emptyList(),
            )
        show(empty)

        composeRule.onNodeWithText("Meanwhile").assertDoesNotExist()
        composeRule
            .onNodeWithText("Ayahs you memorise appear here when they are due.")
            .assertExists()
    }
}
