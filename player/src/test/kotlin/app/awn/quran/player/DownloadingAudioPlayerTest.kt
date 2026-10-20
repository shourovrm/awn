package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.fakes.FakeAudioDownloads
import app.awn.quran.player.fakes.FakeAudioPlayer
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadingAudioPlayerTest {

    private val inner = FakeAudioPlayer()
    private val downloads = FakeAudioDownloads()
    private var amount = DownloadAmount.Page

    private fun TestScope.player() =
        DownloadingAudioPlayer(
            player = inner,
            downloads = downloads,
            quranText = fakeQuranText(),
            downloadAmount = { amount },
            scope = backgroundScope,
        )

    private fun TestScope.observedStates(player: AudioPlayer): List<PlaybackState> {
        val states = mutableListOf<PlaybackState>()
        backgroundScope.launch { player.state.collect { states += it } }
        runCurrent()
        return states
    }

    @Test
    fun missingAudioIsDownloadedThenPlayed() = runTest {
        val player = player()
        val states = observedStates(player)
        val gate = CompletableDeferred<Unit>()
        downloads.gate = gate

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertTrue(states.last() is PlaybackState.Downloading)
        assertEquals(0, inner.playCalls.size)

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(Triple("alafasy", AyahRef(1, 2), AyahRef(1, 3))), inner.playCalls)
        assertTrue(states.last() is PlaybackState.Playing)
    }

    @Test
    fun downloadingStateCarriesBytesDoneAndTotal() = runTest {
        val player = player()
        val states = observedStates(player)
        downloads.gate = CompletableDeferred()

        player.play("alafasy", AyahRef(1, 1), AyahRef(1, 2))
        runCurrent()

        val downloading = states.last() as PlaybackState.Downloading
        assertEquals(0L, downloading.doneBytes)
        assertEquals(200L, downloading.totalBytes)
        assertEquals(0f, downloading.fraction)
    }

    @Test
    fun audioAlreadyOnDiskPlaysWithoutDownloading() = runTest {
        downloads.download("alafasy", listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(1, 3)))
        downloads.downloadCalls.clear()
        val player = player()

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertTrue(downloads.downloadCalls.isEmpty())
        assertEquals(1, inner.playCalls.size)
    }

    @Test
    fun pageAmountDownloadsTheStartingPageOfTheRange() = runTest {
        amount = DownloadAmount.Page
        val player = player()

        player.play("alafasy", AyahRef(2, 1), AyahRef(2, 4))
        runCurrent()

        // The Basmalah (1:1) comes along because the page starts surah 2.
        assertEquals(
            listOf(AyahRef(1, 1), AyahRef(2, 1), AyahRef(2, 2)),
            downloads.downloadCalls.single().second,
        )
    }

    @Test
    fun surahAmountDownloadsTheWholeStartingSurah() = runTest {
        amount = DownloadAmount.Surah
        val player = player()

        player.play("alafasy", AyahRef(2, 3), null)
        runCurrent()

        assertEquals(5, downloads.downloadCalls.single().second.size)
    }

    @Test
    fun aRefusedOrFailedDownloadStreamsInstead() = runTest {
        downloads.failure = DownloadNotAllowedException()
        val player = player()

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(1, inner.playCalls.size)

        downloads.failure = IOException("offline")
        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(2, inner.playCalls.size)
    }

    @Test
    fun stopCancelsTheDownloadAndNothingPlays() = runTest {
        val player = player()
        val states = observedStates(player)
        downloads.gate = CompletableDeferred()

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()
        assertTrue(states.last() is PlaybackState.Downloading)

        player.stop()
        runCurrent()

        assertEquals(PlaybackState.Idle, states.last())
        assertTrue(inner.playCalls.isEmpty())
    }

    @Test
    fun aBadRangeIsHandedToThePlayerToReport() = runTest {
        val player = player()

        player.play("alafasy", AyahRef(1, 99), null)
        runCurrent()

        assertEquals(1, inner.playCalls.size)
        assertTrue(downloads.downloadCalls.isEmpty())
    }
}
