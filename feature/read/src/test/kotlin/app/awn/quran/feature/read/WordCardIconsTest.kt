package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.MyWordState
import app.awn.quran.feature.read.quran.WordPopup
import app.awn.quran.feature.read.quran.WordPopupCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user (2026-10-22): "Add to my words is too big of a text" (chose an icon only), and "previously
 * the word card had play icon, which currently is not available". The card carries two icon
 * buttons around the word, play and add, and no long text label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordCardIconsTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(canPlay: Boolean, state: MyWordState?) {
        val popup =
            WordPopup(
                ayah = AyahRef(1, 1),
                word = AyahWord(WordKey(1, 1, 1), ReadFixture.FIRST_WORD, "In (the) name", "bismi"),
                canPlay = canPlay,
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
                    onAddToMyWords = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun addingIsAnIconWithoutTheLongLabel() {
        show(canPlay = false, state = MyWordState.NotAdded)

        composeRule.onNodeWithContentDescription("Add to my words").assertIsDisplayed()
        composeRule.onNodeWithText("Add to my words").assertDoesNotExist()
    }

    @Test
    fun theCardShowsPlayAndAddTogether() {
        show(canPlay = true, state = MyWordState.NotAdded)

        composeRule.onNodeWithContentDescription("Play this word").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add to my words").assertIsDisplayed()
    }

    @Test
    fun anAddedWordShowsTheFilledIcon() {
        show(canPlay = true, state = MyWordState.Added)

        composeRule.onNodeWithContentDescription("Added to my words").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play this word").assertIsDisplayed()
    }
}
