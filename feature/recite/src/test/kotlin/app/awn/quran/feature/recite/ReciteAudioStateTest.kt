package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciteAudioStateTest {
    private val ayah = AyahRef(1, 1)

    private fun playing(positionMs: Long = 0L, durationMs: Long? = null) =
        PlaybackState.Playing("husary", ayah, null, positionMs, durationMs)

    @Test
    fun idleAndFailedPlaybackLeaveTheBarNormal() {
        assertEquals(ReciteAudioState.Silent, reciteAudioStateOf(PlaybackState.Idle))
        assertEquals(ReciteAudioState.Silent, reciteAudioStateOf(PlaybackState.Error("offline")))
    }

    @Test
    fun everyOtherStateKeepsTheMicrophoneOff() {
        assertTrue(reciteAudioStateOf(playing()).isActive())
        assertTrue(reciteAudioStateOf(PlaybackState.Paused("husary", ayah, 10L)).isActive())
        assertTrue(reciteAudioStateOf(PlaybackState.Downloading(1L, 10L)).isActive())
        assertFalse(reciteAudioStateOf(PlaybackState.Idle).isActive())
    }

    @Test
    fun theListenButtonPlaysPausesAndResumes() {
        assertEquals(ListenAction.PlayAyah, listenActionFor(ReciteAudioState.Silent))
        assertEquals(ListenAction.Pause, listenActionFor(ReciteAudioState.Playing))
        assertEquals(ListenAction.Resume, listenActionFor(ReciteAudioState.Paused))
        assertNull(listenActionFor(ReciteAudioState.Loading))
    }

    @Test
    fun progressIsTheShareOfTheDurationPlayed() {
        assertEquals(0.25f, audioProgressOf(playing(1_000L, 4_000L))!!, 0.001f)
    }

    @Test
    fun progressIsUnknownWithoutADuration() {
        assertNull(audioProgressOf(playing(1_000L, null)))
        assertNull(audioProgressOf(PlaybackState.Idle))
    }

    @Test
    fun progressNeverLeavesZeroToOne() {
        assertEquals(1f, audioProgressOf(playing(9_000L, 4_000L))!!, 0.001f)
    }

    @Test
    fun aDownloadShowsItsOwnFraction() {
        assertEquals(0.5f, audioProgressOf(PlaybackState.Downloading(5L, 10L))!!, 0.001f)
    }
}
