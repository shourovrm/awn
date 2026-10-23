package app.awn.quran.services

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackState
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPlaybackTest {

    @Test
    fun aWordReturnsOnlyWhenItHasFinishedPlaying() = runBlocking {
        val player = FakeAudioPlayer()

        val playing =
            launch(start = CoroutineStart.UNDISPATCHED) {
                player.playWordToEnd("husary", AyahRef(2, 6), 1)
            }
        yield()
        assertFalse("still sounding", playing.isCompleted)

        player.currentState.value = PlaybackState.Idle
        playing.join()

        assertTrue(playing.isCompleted)
    }
}
