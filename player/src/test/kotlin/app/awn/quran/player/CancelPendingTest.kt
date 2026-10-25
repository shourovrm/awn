package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.fakes.FakeAudioDownloads
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leaving the Quran screen drops a request that has not started, and never silences playback. */
@OptIn(ExperimentalCoroutinesApi::class)
class CancelPendingTest {

    private val inner = FakeAudioPlayer()
    private val downloads = FakeAudioDownloads()

    private fun TestScope.player() =
        DownloadingAudioPlayer(
            player = inner,
            downloads = downloads,
            quranText = fakeQuranText(),
            downloadAmount = { DownloadAmount.Page },
            scope = backgroundScope,
        )

    private fun TestScope.observedStates(player: AudioPlayer): List<PlaybackState> {
        val states = mutableListOf<PlaybackState>()
        backgroundScope.launch { player.state.collect { states += it } }
        runCurrent()
        return states
    }

    @Test
    fun cancelWhileDownloadingStartsNoPlayback() = runTest {
        val player = player()
        val states = observedStates(player)
        val gate = CompletableDeferred<Unit>()
        downloads.gate = gate

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()
        assertTrue(states.last() is PlaybackState.Downloading)

        player.cancelPending()
        gate.complete(Unit)
        runCurrent()

        assertEquals(PlaybackState.Idle, states.last())
        assertTrue(inner.playCalls.isEmpty())
    }

    @Test
    fun cancelWhilePlayingChangesNothing() = runTest {
        downloads.download("alafasy", listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(1, 3)))
        val player = player()
        val states = observedStates(player)
        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()
        val playing = states.last()
        assertTrue(playing is PlaybackState.Playing)

        player.cancelPending()
        runCurrent()

        assertEquals(playing, states.last())
        assertEquals(1, inner.playCalls.size)
        assertEquals(0, inner.stopCalls)
    }
}
