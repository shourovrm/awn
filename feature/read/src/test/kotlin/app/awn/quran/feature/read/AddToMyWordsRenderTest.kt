package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.MyWordState
import app.awn.quran.feature.read.quran.WordPopup
import app.awn.quran.feature.read.quran.WordPopupCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The word card on the Quran page offers "Add to my words", which queues the word for the Learn
 * course. :feature:read does not know the course: :app passes the state and the action in, and a
 * screen without the course (null state) shows no button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddToMyWordsRenderTest {
    @get:Rule val composeRule = createComposeRule()

    private var adds = 0

    private fun show(state: MyWordState?) {
        val popup =
            WordPopup(
                ayah = AyahRef(1, 1),
                word = AyahWord(WordKey(1, 1, 1), ReadFixture.FIRST_WORD, "In (the) name", "bismi"),
            )
        composeRule.setContent {
            AwnTheme {
                WordPopupCard(
                    popup = popup,
                    anchor = null,
                    topClearancePx = 0f,
                    onDismiss = {},
                    isPlaying = false,
                    onPlay = {},
                    onStop = {},
                    onDownloadAudio = {},
                    myWordState = state,
                    onAddToMyWords = { adds += 1 },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aWordNotYetAddedOffersTheButton() {
        show(MyWordState.NotAdded)

        composeRule.onNodeWithText("Add to my words").performClick()

        assertEquals(1, adds)
    }

    @Test
    fun anAddedWordSaysSoAndOffersNothingMore() {
        show(MyWordState.Added)

        composeRule.onNodeWithText("Added to my words").assertIsDisplayed()
        composeRule.onNodeWithText("Add to my words").assertDoesNotExist()
    }

    @Test
    fun withoutTheCourseThereIsNoButton() {
        show(null)

        composeRule.onNodeWithText("Add to my words").assertDoesNotExist()
        composeRule.onNodeWithText("Added to my words").assertDoesNotExist()
    }
}
