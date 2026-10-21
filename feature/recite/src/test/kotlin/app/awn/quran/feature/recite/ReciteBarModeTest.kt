package app.awn.quran.feature.recite

import org.junit.Assert.assertEquals
import org.junit.Test

class ReciteBarModeTest {

    @Test
    fun theBarWaitsWhileTheModelIsNotReady() {
        assertEquals(ReciteBarMode.Preparing, reciteBarMode(SpeechState.Idle))
        assertEquals(ReciteBarMode.Preparing, reciteBarMode(SpeechState.Preparing))
    }

    @Test
    fun theControlsAppearWhenTheModelIsReady() {
        assertEquals(ReciteBarMode.Controls, reciteBarMode(SpeechState.Ready))
    }

    @Test
    fun aFailedPreparationShowsTheControlsSoTheMicrophoneCanRetry() {
        assertEquals(ReciteBarMode.Controls, reciteBarMode(SpeechState.Failed))
    }
}
