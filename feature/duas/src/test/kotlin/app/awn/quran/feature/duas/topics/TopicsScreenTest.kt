package app.awn.quran.feature.duas.topics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import app.awn.quran.feature.duas.DuasTestEnvironment
import app.awn.quran.feature.duas.PronunciationState
import app.awn.quran.feature.duas.ui.PRONUNCIATION_DOWNLOAD_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WAIT_MILLIS = 5_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class TopicsScreenTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val environment = DuasTestEnvironment()
    private val openedDuasGroups = mutableListOf<String>()

    private fun show() {
        val dependencies = environment.dependencies()
        composeRule.setContent {
            TopicsScreen(
                dependencies = dependencies,
                onBack = {},
                onOpenQuran = { _, _ -> },
                onOpenDuasGroup = { openedDuasGroups += it },
            )
        }
        waitForText("Parents")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aTopicShowsItsPartsAndAPartOpensItsCards() {
        show()

        composeRule.onNodeWithText("Family · 2 ayahs").assertIsDisplayed()
        composeRule.onNodeWithText("Parents").performClick()
        composeRule.onNodeWithText("Go through all 2 ayahs").assertIsDisplayed()
        composeRule.onNodeWithText("Be good to your parents").performClick()

        waitForText("1 of 1")
        composeRule.onNodeWithText("Parents · Be good to your parents").assertIsDisplayed()
    }

    @Test
    fun goingThroughAllAyahsOpensOneDeckOfEveryPart() {
        show()

        composeRule.onNodeWithText("Parents").performClick()
        composeRule.onNodeWithTag(TOPIC_ALL_TAG).performClick()

        waitForText("1 of 2")
    }

    @Test
    fun theRelatedDuasRowOpensThatGroupInDuas() {
        show()

        composeRule.onNodeWithText("Parents").performClick()
        waitForText("Parents and family")
        composeRule.onNodeWithText("Parents and family").performClick()

        assertEquals(listOf("parents"), openedDuasGroups)
    }

    @Test
    fun searchFindsAyahsByTheTopicName() {
        show()

        composeRule.onNode(hasSetTextAction()).performTextInput("both worlds")

        waitForText("Ayahs · 1")
    }

    @Test
    fun wordByWordOffersThePronunciationDownloadWhenItIsMissing() {
        environment.pronunciation.value = PronunciationState.Missing
        show()

        composeRule.onNodeWithText("Parents").performClick()
        composeRule.onNodeWithText("Be good to your parents").performClick()
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodesWithTag("dua-layers-button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dua-layers-button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dua-layer-word-by-word").performClick()
        // The menu stays open for more ticks, as in the Mushaf; Back closes it.
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRONUNCIATION_DOWNLOAD_TAG).performClick()

        assertEquals(1, environment.pronunciationRequests)
    }
}
