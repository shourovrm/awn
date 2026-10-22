package app.awn.quran.feature.read

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.BusyBarButton
import app.awn.quran.feature.read.quran.BusyState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user (build 227): "the bar hides with a pause button, pressing the button shows the bar, but
 * doesn't pause." The corner button now does what its icon says: Pause pauses the audio, Play
 * resumes it, and while reciting it stops or starts the microphone. A tap on the page brings the
 * full bar back, as before. Ending a recitation stays on the bar's Stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusyBarButtonTest {
    @get:Rule val composeRule = createComposeRule()

    private var toggles = 0

    private fun show(state: BusyState) {
        composeRule.setContent {
            AwnTheme { BusyBarButton(state = state, onToggle = { toggles += 1 }) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun whileAudioPlaysThePauseButtonPauses() {
        show(BusyState.AudioPlaying)

        composeRule.onNodeWithContentDescription("Pause").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun whileAudioIsPausedThePlayButtonResumes() {
        show(BusyState.AudioPaused)

        composeRule.onNodeWithContentDescription("Play").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun whileTheMicrophoneListensTheButtonStopsIt() {
        show(BusyState.MicrophoneOn)

        composeRule.onNodeWithContentDescription("Stop microphone").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun whileTheMicrophoneIsOffTheButtonStartsIt() {
        show(BusyState.MicrophoneOff)

        composeRule.onNodeWithContentDescription("Start microphone").performClick()

        assertEquals(1, toggles)
    }
}
