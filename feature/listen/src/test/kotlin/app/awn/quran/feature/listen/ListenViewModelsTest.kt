package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.player.AudioDownloads
import app.awn.quran.player.AudioStatus
import app.awn.quran.player.DownloadNotAllowedException
import app.awn.quran.player.DownloadProgress
import app.awn.quran.player.DownloadState
import app.awn.quran.player.PlaybackState
import app.awn.quran.player.fakes.FakeAudioDownloads
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settings() = ListenSettings(InMemoryPreferences())

    private val reciters =
        FakeReciterRepository(
            reciterList =
                mutableListOf(
                    Reciter("alafasy", "Alafasy", "s{SSS}", "a{SSS}{AAA}", true, "alafasy"),
                    Reciter("raad", "Raad", "s{SSS}", null, false, null),
                ),
        )

    private val quranText =
        FakeQuranTextRepository(
            chapterList =
                listOf(
                    Chapter(1, "", "Al-Fatihah", null, 3, "makkah", true),
                    Chapter(2, "", "Al-Baqarah", null, 4, "madinah", true),
                ),
        )

    private fun TestScope.listenViewModel(player: FakeAudioPlayer) =
        ListenViewModel(player, reciters, quranText, settings()).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
        }

    @Test
    fun startPlaysWithSelectedReciterAndShowsAyahPosition() = runTest {
        val player = FakeAudioPlayer()
        val viewModel = listenViewModel(player)

        viewModel.start(AyahRef(1, 1), null)

        advanceUntilIdle()

        assertEquals(Triple("alafasy", AyahRef(1, 1), AyahRef(1, 3)), player.playCalls.single())
        val state = viewModel.state.value
        assertTrue(state.isVisible)
        assertTrue(state.isPlaying)
        assertEquals("Alafasy", state.reciterName)
        assertEquals(1, state.ayahNumber)
        assertEquals(3, state.ayahCount)
    }

    @Test
    fun nextAndPreviousPlayTheNeighbouringAyahOfTheSameRange() = runTest {
        val player = FakeAudioPlayer()
        val viewModel = listenViewModel(player)
        viewModel.start(AyahRef(1, 1), null)
        advanceUntilIdle()

        viewModel.next()

        advanceUntilIdle()
        assertEquals(AyahRef(1, 2), player.playCalls.last().second)
        viewModel.previous()
        advanceUntilIdle()
        assertEquals(AyahRef(1, 1), player.playCalls.last().second)

        // Before the first ayah there is nothing to step to.
        val callCount = player.playCalls.size
        viewModel.previous()
        advanceUntilIdle()
        assertEquals(callCount, player.playCalls.size)
    }

    @Test
    fun togglePausesAndResumes() = runTest {
        val player = FakeAudioPlayer()
        val viewModel = listenViewModel(player)
        viewModel.start(AyahRef(1, 1), null)
        advanceUntilIdle()

        viewModel.togglePlayPause()

        advanceUntilIdle()
        assertTrue(player.currentState.value is PlaybackState.Paused)
        assertFalse(viewModel.state.value.isPlaying)
        viewModel.togglePlayPause()
        advanceUntilIdle()
        assertTrue(player.currentState.value is PlaybackState.Playing)
    }

    @Test
    fun repeatButtonStepsThroughChoicesAndWraps() = runTest {
        val player = FakeAudioPlayer()
        val viewModel = listenViewModel(player)

        val seen = mutableListOf<Int>()
        repeat(ListenViewModel.REPEAT_CHOICES.size) {
            viewModel.cycleRepeat()
            seen += player.repeatTimes
        }

        assertEquals(listOf(1, 3, 5, 10, 0), seen)
    }

    @Test
    fun invalidRangeShowsErrorWithoutPlaying() = runTest {
        val player = FakeAudioPlayer()
        val viewModel = listenViewModel(player)

        viewModel.start(AyahRef(1, 9), null)

        advanceUntilIdle()

        assertTrue(player.playCalls.isEmpty())
        assertTrue(viewModel.state.value.hasError)
    }

    @Test
    fun recitersListShowsSizesSelectionAndRemovesDownloads() = runTest {
        val downloads = FakeAudioDownloads(bytesPerSurah = 500L)
        downloads.downloadSurah("raad", 1)
        downloads.downloadSurah("raad", 2)
        val settings = settings()
        val viewModel = RecitersViewModel(reciters, downloads, settings)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val rows = viewModel.state.value.rows.associateBy { it.id }
        assertEquals(0L, rows.getValue("alafasy").downloadedBytes)
        assertEquals(1_000L, rows.getValue("raad").downloadedBytes)
        assertTrue(rows.getValue("alafasy").isSelected)
        assertTrue(rows.getValue("alafasy").wordByWord)
        assertFalse(rows.getValue("raad").wordByWord)

        viewModel.select("raad")

        advanceUntilIdle()
        assertTrue(viewModel.state.value.rows.first { it.id == "raad" }.isSelected)

        viewModel.removeDownloads("raad")

        advanceUntilIdle()
        assertEquals(0L, viewModel.state.value.rows.first { it.id == "raad" }.downloadedBytes)

        viewModel.setWifiOnly(false)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.wifiOnly)
    }

    @Test
    fun wifiOnlyDefaultsToOn() = runTest {
        val settings = settings()
        assertTrue(settings.wifiOnly.first())
        assertEquals(ListenSettings.DEFAULT_RECITER_ID, settings.selectedReciterId.first())
    }

    @Test
    fun downloadsScreenDownloadsSelectedReciterAndMapsRefusalToMessage() = runTest {
        val downloads = FakeAudioDownloads()
        val settings = settings()
        val viewModel =
            DownloadsViewModel(downloads, quranText, settings, CoroutineScope(dispatcher))

        assertEquals(2, viewModel.chapters.value.size)
        viewModel.download(2)
        advanceUntilIdle()
        assertTrue(downloads.state("alafasy", 2).first().complete)

        val refusing =
            DownloadsViewModel(RefusingDownloads, quranText, settings, CoroutineScope(dispatcher))
        refusing.download(1)
        advanceUntilIdle()
        assertEquals(DownloadMessage.WifiRequired, refusing.message.value)
        refusing.dismissMessage()
        assertEquals(DownloadMessage.None, refusing.message.value)
    }

    @Test
    fun clockFormatsMinutesAndHours() {
        assertEquals("0:00", formatClock(0))
        assertEquals("1:12", formatClock(72_400))
        assertEquals("1:01:05", formatClock(3_665_000))
    }

    @Test
    fun progressIsPositionOverDurationAndZeroWhenUnknown() {
        assertEquals(0.5f, ListenPanelState(positionMs = 500, durationMs = 1_000).progress)
        assertEquals(0f, ListenPanelState(positionMs = 500, durationMs = null).progress)
    }

    private object RefusingDownloads : AudioDownloads {
        override fun state(reciterId: String, surah: Int): Flow<DownloadState> = emptyFlow()

        override suspend fun downloadPage(reciterId: String, ayahs: List<AyahRef>) =
            throw DownloadNotAllowedException()

        override suspend fun downloadSurah(reciterId: String, surah: Int) =
            throw DownloadNotAllowedException()

        override suspend fun download(
            reciterId: String,
            ayahs: List<AyahRef>,
            onProgress: (DownloadProgress) -> Unit,
        ) = throw DownloadNotAllowedException()

        override suspend fun bytesUsed(reciterId: String) = 0L

        override fun usedBytes(reciterId: String): Flow<Long> = flowOf(0L)

        override suspend fun status(reciterId: String, ayahs: List<AyahRef>) =
            AudioStatus(0L, 0, ayahs.size)

        override suspend fun estimateBytes(reciterId: String, ayahs: List<AyahRef>): Long? = null

        override suspend fun removeAyahs(reciterId: String, ayahs: List<AyahRef>) = Unit

        override suspend fun remove(reciterId: String) = Unit
    }
}
