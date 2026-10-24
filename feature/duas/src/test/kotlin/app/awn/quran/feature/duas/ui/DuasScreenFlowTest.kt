package app.awn.quran.feature.duas.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.duas.DuasTestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home, the cards behind a tap, Back, and the link to the Quran screen, with the real view
 * model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DuasScreenFlowTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val environment = DuasTestEnvironment()
    private var wentBack = 0
    private val openedInQuran = mutableListOf<Pair<Int, AyahRef>>()

    private fun show() {
        val dependencies = environment.dependencies()
        composeRule.setContent {
            DuasScreen(
                dependencies = dependencies,
                onBack = { wentBack++ },
                onOpenQuran = { page, ayah -> openedInQuran += page to ayah },
            )
        }
        // The collection is read on a background thread.
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodesWithText("Groups").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun aGroupOpensItsCardsAndBackReturnsToTheHome() {
        show()

        composeRule.onNodeWithText("Good in this world and the next").performClick()
        waitForText("1 of 2")
        composeRule.onNodeWithText("Al-Baqarah 2:201").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Groups").assertIsDisplayed()
        assertEquals(0, wentBack)
    }

    @Test
    fun theStartersOpenOneCard() {
        show()

        composeRule.onNodeWithText("Ten short duas to start").performClick()

        waitForText("1 of 1")
    }

    @Test
    fun backOnTheHomeLeavesTheCollection() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, wentBack)
    }

    @Test
    fun openInTheMushafGivesThePageAndTheFirstAyahOfTheDua() {
        show()
        composeRule.onNodeWithText("Good in this world and the next").performClick()
        waitForText("Open in the Mushaf")

        composeRule.onNodeWithText("Open in the Mushaf").performClick()
        composeRule.waitUntil(WAIT_MILLIS) { openedInQuran.isNotEmpty() }

        assertEquals(listOf(31 to AyahRef(2, 201)), openedInQuran)
    }

    @Test
    fun savingADuaChangesTheButtonAndTheSavedFilterFindsIt() {
        show()
        composeRule.onNodeWithText("Good in this world and the next").performClick()
        waitForText("1 of 2")

        composeRule.onNodeWithContentDescription("Save dua").performClick()
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule
                .onAllNodesWithContentDescription("Remove from saved")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        pressBack()
        composeRule.onNodeWithText("Saved").performClick()

        // The bar and the one group left both say "1 dua".
        composeRule.onAllNodesWithText("1 dua").assertCountEquals(2)
        composeRule.onNodeWithText("Parents and family").assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
