package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.LearnField
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.PathState
import app.awn.quran.feature.learn.course.TrackSummary
import app.awn.quran.feature.learn.course.TrackUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A user who chose to start with the letters saw every Tajweed unit as a dead row: the rows looked
 * like the open ones but a tap did nothing. The note under the title says when to start; the units
 * open at any time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun aTajweedUnitOpensBeforeReadingUnitSevenIsDone() {
        val unit = TrackUnit("makharij", 1, "Where letters come from", PathState.Next)
        val summary =
            TrackSummary(LearnTrack.Tajweed, 0, 1, next = unit, opensAfterReadingUnit = 7)
        val opened = mutableListOf<String>()

        composeRule.setContent {
            AwnTheme(field = LearnField) {
                TrackScreen(
                    summary = summary,
                    units = listOf(unit),
                    onOpen = { opened += it.id },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("1 · Where letters come from").performClick()

        assertEquals(listOf("makharij"), opened)
    }
}
