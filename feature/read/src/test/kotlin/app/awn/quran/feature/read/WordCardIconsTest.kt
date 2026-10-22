package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The user chose layout D (2026-10-22): the Arabic word alone on top, then two labelled buttons
 * under the meaning, Play and Save word, which reads Saved once the word is in the list.
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
    fun withoutWordTimingsThereIsNoPlayButton() {
        show(canPlay = false, state = MyWordState.NotAdded)

        composeRule.onNodeWithText("Save word").assertIsDisplayed()
        composeRule.onNodeWithText("Play").assertDoesNotExist()
    }

    @Test
    fun theCardShowsPlayAndAddTogether() {
        show(canPlay = true, state = MyWordState.NotAdded)

        composeRule.onNodeWithText("Play").assertIsDisplayed()
        composeRule.onNodeWithText("Save word").assertIsDisplayed()
    }

    @Test
    fun anAddedWordReadsSaved() {
        show(canPlay = true, state = MyWordState.Added)

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("Play").assertIsDisplayed()
    }
}
