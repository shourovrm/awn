package app.awn.quran.feature.listen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The combined bar the user chose (design A): one bar in one place, Play at the start and the
 * microphone at the end. The audio bar now carries the microphone when idle and while playing, and
 * the playing bar has Stop next to Pause. When the screen gives no recite action, no microphone
 * shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioBarReciteButtonTest {
    @get:Rule val composeRule = createComposeRule()

    private var recites = 0
    private var stops = 0

    private fun actions(withRecite: Boolean) =
        AudioBarActions(
            onPlay = {},
            onPause = {},
            onStop = { stops += 1 },
            onPrevious = {},
            onNext = {},
            onRepeat = {},
            onSpeed = {},
            onCancelDownload = {},
            onOpenReciters = {},
            onExpand = {},
            onChooseStart = {},
            onDismissStartChoices = {},
            onDownload = {},
            onStream = {},
            onRecite = if (withRecite) ({ recites += 1 }) else null,
        )

    private fun show(state: AudioBarState, withRecite: Boolean = true) {
        composeRule.setContent {
            AwnTheme { AudioBarContent(state, startChoices = emptyList(), actions(withRecite)) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theIdleBarEndsWithTheMicrophone() {
        show(AudioBarState.Idle(reciterName = "Mishary Alafasy", hasError = false))

        composeRule.onNodeWithContentDescription("Recite").performClick()

        assertEquals(1, recites)
    }

    @Test
    fun thePlayingBarHasTheMicrophone() {
        show(AudioBarState.Active(isPlaying = true, repeatCount = null, speed = null))

        composeRule.onNodeWithContentDescription("Recite").performClick()

        assertEquals(1, recites)
    }

    @Test
    fun thePlayingBarHasStopAndPause() {
        show(AudioBarState.Active(isPlaying = true, repeatCount = null, speed = null))

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop").performClick()

        assertEquals(1, stops)
    }

    @Test
    fun withoutAReciteActionThereIsNoMicrophone() {
        show(
            AudioBarState.Idle(reciterName = "Mishary Alafasy", hasError = false),
            withRecite = false,
        )

        composeRule.onNodeWithContentDescription("Recite").assertDoesNotExist()
    }
}
