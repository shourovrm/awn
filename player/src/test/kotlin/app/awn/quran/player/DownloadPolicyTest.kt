package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.fakes.FakeAudioDownloads
import app.awn.quran.player.fakes.FakeAudioPlayer
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class FixedNetwork(var online: Boolean = true, var metered: Boolean = false) :
    NetworkStatus {
    override fun isOnline() = online

    override fun isMetered() = metered
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadPolicyTest {

    private val inner = FakeAudioPlayer()
    private val downloads = FakeAudioDownloads()
    private val network = FixedNetwork()
    private var freeBytes = Long.MAX_VALUE

    private fun TestScope.player() =
        DownloadingAudioPlayer(
            player = inner,
            downloads = downloads,
            quranText = fakeQuranText(),
            downloadAmount = { DownloadAmount.Page },
            scope = backgroundScope,
            runner = DirectDownloadRunner(downloads) { freeBytes },
            network = network,
        )

    private fun TestScope.statesOf(player: AudioPlayer): MutableList<PlaybackState> {
        val states = mutableListOf<PlaybackState>()
        backgroundScope.launch { player.state.collect { states += it } }
        runCurrent()
        return states
    }

    @Test
    fun notEnoughSpaceNamesBothNumbers() = runTest {
        downloads.estimate = 120_000_000L

        val failure =
            runCatching {
                downloads.requireFreeSpace("alafasy", listOf(AyahRef(1, 1))) { 40_000_000L }
            }
                .exceptionOrNull() as InsufficientSpaceException

        assertEquals(120_000_000L, failure.neededBytes)
        assertEquals(40_000_000L, failure.freeBytes)
        assertEquals(
            "Needs 120 MB, 40 MB free",
            DownloadProblem.classify(failure, online = true).englishMessage(),
        )
    }

    @Test
    fun spaceAlreadyUsedByPresentFilesIsNotNeededAgain() = runTest {
        downloads.estimate = 10_000L
        downloads.download("alafasy", listOf(AyahRef(1, 1), AyahRef(1, 2)))

        // Two of three ayahs (100 bytes each in the fake) are on disk, so 9 800 bytes remain.
        downloads.requireFreeSpace("alafasy", listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(1, 3))) {
            9_800L
        }
        assertThrows(InsufficientSpaceException::class.java) {
            kotlinx.coroutines.runBlocking {
                downloads.requireFreeSpace(
                    "alafasy",
                    listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(1, 3)),
                ) {
                    9_799L
                }
            }
        }
    }

    @Test
    fun unknownSizeNeverBlocksADownload() = runTest {
        downloads.estimate = null
        downloads.requireFreeSpace("alafasy", listOf(AyahRef(1, 1))) { 0L }
    }

    @Test
    fun failuresAreNamedByCause() {
        val offline = UnknownHostException("host")
        assertEquals(DownloadProblem.NoInternet, DownloadProblem.classify(offline, online = false))
        assertEquals(
            DownloadProblem.ServerNotReachable,
            DownloadProblem.classify(IOException("503"), online = true),
        )
        assertEquals(
            DownloadProblem.WifiOnly,
            DownloadProblem.classify(DownloadNotAllowedException(), online = true),
        )
        assertEquals("No internet", DownloadProblem.NoInternet.englishMessage())
        assertEquals("Server not reachable", DownloadProblem.ServerNotReachable.englishMessage())
    }

    @Test
    fun aProblemSurvivesTheTripThroughAWorkRequest() {
        val problems =
            listOf(
                DownloadProblem.NotEnoughSpace(120_000_000L, 40_000_000L),
                DownloadProblem.NoInternet,
                DownloadProblem.ServerNotReachable,
                DownloadProblem.WifiOnly,
            )
        for (problem in problems) {
            assertEquals(problem, DownloadProblem.fromFields(problem.toFields()))
        }
        assertNull(DownloadProblem.fromFields(emptyMap()))
    }

    @Test
    fun twentyMegabytesOnWifiDownloadsAtOnce() = runTest {
        downloads.estimate = 20_000_000L
        val player = player()
        val states = statesOf(player)

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(1, downloads.downloadCalls.size)
        assertNull(player.downloadPrompt.value)
        assertEquals(1, inner.playCalls.size)
    }

    @Test
    fun overTwentyMegabytesAsksFirstWithTheSize() = runTest {
        downloads.estimate = 20_000_001L
        val player = player()
        val states = statesOf(player)

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(DownloadPrompt(20_000_001L, metered = false), player.downloadPrompt.value)
        assertTrue(downloads.downloadCalls.isEmpty())
        assertTrue(inner.playCalls.isEmpty())
    }

    @Test
    fun aMeteredNetworkAsksEvenForASmallDownload() = runTest {
        network.metered = true
        downloads.estimate = 3_200_000L
        val player = player()
        val states = statesOf(player)

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(DownloadPrompt(3_200_000L, metered = true), player.downloadPrompt.value)
    }

    @Test
    fun answeringDownloadFetchesThenPlays() = runTest {
        downloads.estimate = 30_000_000L
        val player = player()
        val states = statesOf(player)
        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        player.answerDownloadPrompt(true)
        runCurrent()

        assertEquals(1, downloads.downloadCalls.size)
        assertEquals(1, inner.playCalls.size)
        assertTrue(states.last() is PlaybackState.Playing)
    }

    @Test
    fun answeringStreamPlaysWithoutDownloading() = runTest {
        downloads.estimate = 30_000_000L
        val player = player()
        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        player.answerDownloadPrompt(false)
        runCurrent()

        assertTrue(downloads.downloadCalls.isEmpty())
        assertEquals(1, inner.playCalls.size)
    }

    @Test
    fun stoppingWhileAskedPlaysNothing() = runTest {
        downloads.estimate = 30_000_000L
        val player = player()
        val states = statesOf(player)
        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        player.stop()
        runCurrent()

        assertEquals(PlaybackState.Idle, states.last())
        assertTrue(inner.playCalls.isEmpty())
    }

    @Test
    fun aDownloadTheUserChoseThatRunsOutOfSpaceShowsTheNumbers() = runTest {
        downloads.estimate = 120_000_000L
        freeBytes = 40_000_000L
        val player = player()
        val states = statesOf(player)
        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        player.answerDownloadPrompt(true)
        runCurrent()

        val error = states.last() as PlaybackState.Error
        assertEquals(DownloadProblem.NotEnoughSpace(120_000_000L, 40_000_000L), error.problem)
        assertEquals("Needs 120 MB, 40 MB free", error.message)
        assertTrue(inner.playCalls.isEmpty())
    }

    @Test
    fun aSmallAutomaticDownloadThatFailsStreamsInstead() = runTest {
        downloads.failure = IOException("503")
        val player = player()

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(1, inner.playCalls.size)
    }

    @Test
    fun offlineWithMissingAudioSaysNoInternet() = runTest {
        network.online = false
        downloads.failure = UnknownHostException("host")
        val player = player()
        val states = statesOf(player)

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(DownloadProblem.NoInternet, (states.last() as PlaybackState.Error).problem)
        assertTrue(inner.playCalls.isEmpty())
    }

    @Test
    fun aRangeThatStartsASurahAlsoFetchesTheBasmalah() = runTest {
        val player = player()

        player.play("alafasy", AyahRef(2, 1), AyahRef(2, 2))
        runCurrent()

        val fetched = downloads.downloadCalls.single().second
        assertEquals(AyahRef(1, 1), fetched.first())
        assertTrue(AyahRef(2, 1) in fetched)
    }

    @Test
    fun audioAlreadyOnDiskPlaysWithNoSizeRequestAndNoPrompt() = runTest {
        network.metered = true
        downloads.download("alafasy", listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(1, 3)))
        val player = player()
        val states = statesOf(player)

        player.play("alafasy", AyahRef(1, 2), AyahRef(1, 3))
        runCurrent()

        assertEquals(0, downloads.estimateCalls)
        assertNull(player.downloadPrompt.value)
        assertEquals(1, inner.playCalls.size)
    }
}
