package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.AyahWord
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.MyWordState
import app.awn.quran.feature.read.quran.WordPopup
import app.awn.quran.feature.read.quran.WordPopupCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user chose layout D (2026-10-22): the Arabic word alone on top, then two round icon buttons
 * under the meaning, round Play and Save word icon buttons; the save button is named Saved once the
 * word is in the list.
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

        composeRule.onNodeWithContentDescription("Save word").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun theCardShowsPlayAndAddTogether() {
        show(canPlay = true, state = MyWordState.NotAdded)

        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save word").assertIsDisplayed()
    }

    @Test
    fun anAddedWordReadsSaved() {
        show(canPlay = true, state = MyWordState.Added)

        composeRule.onNodeWithContentDescription("Saved").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }
}
