package app.awn.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.quran.BusyState
import app.awn.quran.feature.recite.RecitationPhase
import app.awn.quran.player.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The corner button shows the audio while any plays, else the microphone while reciting. */
class BusyBarToggleTest {
    private val playing = PlaybackState.Playing("husary", AyahRef(1, 1), null, 0L, null)
    private val paused = PlaybackState.Paused("husary", AyahRef(1, 1), 0L)

    @Test
    fun playingAudioShowsPauseEvenWhileReciting() {
        assertEquals(
            BusyState.AudioPlaying,
            busyStateOf(playing, isReciting = true, phase = RecitationPhase.Stopped),
        )
    }

    @Test
    fun pausedAudioShowsPlay() {
        assertEquals(
            BusyState.AudioPaused,
            busyStateOf(paused, isReciting = false, phase = RecitationPhase.Idle),
        )
    }

    @Test
    fun aListeningRecitationShowsStopMicrophone() {
        assertEquals(
            BusyState.MicrophoneOn,
            busyStateOf(PlaybackState.Idle, isReciting = true, phase = RecitationPhase.Listening),
        )
    }

    @Test
    fun aStoppedRecitationShowsStartMicrophone() {
        assertEquals(
            BusyState.MicrophoneOff,
            busyStateOf(PlaybackState.Idle, isReciting = true, phase = RecitationPhase.Stopped),
        )
    }

    @Test
    fun nothingBusyShowsNothing() {
        assertNull(
            busyStateOf(PlaybackState.Idle, isReciting = false, phase = RecitationPhase.Idle),
        )
    }
}
