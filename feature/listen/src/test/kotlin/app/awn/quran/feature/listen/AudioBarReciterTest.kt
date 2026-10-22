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
 * The user could not change the reciter from the Quran page: the only way in was a tap on the
 * reciter's name in the idle bar, which looks like a label, is empty before the names load and
 * turns into an error message after a failure; while audio played there was no way at all. A
 * labelled reciter button now sits in the bar whenever it is idle or playing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioBarReciterTest {
    @get:Rule val composeRule = createComposeRule()

    private var reciterSheetOpens = 0

    private val actions =
        AudioBarActions(
            onPlay = {},
            onPause = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onRepeat = {},
            onSpeed = {},
            onCancelDownload = {},
            onOpenReciters = { reciterSheetOpens += 1 },
            onExpand = {},
            onChooseStart = {},
            onDismissStartChoices = {},
            onDownload = {},
            onStream = {},
        )

    private fun show(state: AudioBarState) {
        composeRule.setContent {
            AwnTheme { AudioBarContent(state, startChoices = emptyList(), actions = actions) }
        }
        composeRule.waitForIdle()
    }

    private fun tapReciterButton() {
        composeRule.onNodeWithContentDescription("Choose reciter").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Choose reciter").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theIdleBarHasAReciterButton() {
        show(AudioBarState.Idle(reciterName = "Mishary Alafasy", hasError = false))

        tapReciterButton()

        assertEquals(1, reciterSheetOpens)
    }

    @Test
    fun theReciterButtonIsThereBeforeTheNamesLoad() {
        show(AudioBarState.Idle(reciterName = "", hasError = false))

        tapReciterButton()

        assertEquals(1, reciterSheetOpens)
    }

    @Test
    fun theReciterButtonIsThereAfterAFailure() {
        show(AudioBarState.Idle(reciterName = "Mishary Alafasy", hasError = true))

        tapReciterButton()

        assertEquals(1, reciterSheetOpens)
    }

    @Test
    fun thePlayingBarHasAReciterButton() {
        show(AudioBarState.Active(isPlaying = true, repeatCount = null, speed = null))

        tapReciterButton()

        assertEquals(1, reciterSheetOpens)
    }

    @Test
    fun thePausedBarHasAReciterButton() {
        show(AudioBarState.Active(isPlaying = false, repeatCount = null, speed = null))

        tapReciterButton()

        assertEquals(1, reciterSheetOpens)
    }
}
