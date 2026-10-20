package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeAudioPlayerTest {

    @Test
    fun pauseKeepsAyahAndPosition() = runBlocking {
        val player = FakeAudioPlayer()
        player.play("husary", AyahRef(1, 1), null)
        player.pause()
        assertEquals(PlaybackState.Paused("husary", AyahRef(1, 1), 0L), player.state.first())
    }
}
