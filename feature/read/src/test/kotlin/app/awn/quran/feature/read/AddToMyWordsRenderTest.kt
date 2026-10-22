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
 * The word card on the Quran page offers a labelled "Save word" button (layout D of the 2026-10-22
 * mock-ups; "Saved" once the word is in the list, and a tap on that removes it), which queues the
 * word for the Learn course. :feature:read does not know the course: :app passes the state and the
 * action in, and a screen without the course (null state) shows no button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddToMyWordsRenderTest {
    @get:Rule val composeRule = createComposeRule()

    private var adds = 0
    private var removes = 0

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
                    onRemoveFromMyWords = { removes += 1 },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aWordNotYetAddedOffersTheButton() {
        show(MyWordState.NotAdded)

        composeRule.onNodeWithText("Save word").performClick()

        assertEquals(1, adds)
    }

    @Test
    fun anAddedWordSaysSoAndATapTakesItOut() {
        show(MyWordState.Added)

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("Save word").assertDoesNotExist()

        composeRule.onNodeWithText("Saved").performClick()

        assertEquals(1, removes)
        assertEquals(0, adds)
    }

    @Test
    fun withoutTheCourseThereIsNoButton() {
        show(null)

        composeRule.onNodeWithText("Save word").assertDoesNotExist()
        composeRule.onNodeWithText("Saved").assertDoesNotExist()
    }
}
