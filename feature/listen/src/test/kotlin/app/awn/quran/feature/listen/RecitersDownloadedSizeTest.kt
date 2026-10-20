package app.awn.quran.feature.listen

import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.player.fakes.FakeAudioDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Defect 4: the Reciters list said "Nothing downloaded" after a successful download, because the
 * view model read the size of each reciter once, when it was created.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecitersDownloadedSizeTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val reciters =
        FakeReciterRepository(
            reciterList =
                mutableListOf(Reciter("alafasy", "Alafasy", "s{SSS}", "a{SSS}{AAA}", true, null)),
        )

    @Test
    fun sizeAppearsWhenADownloadFinishesAfterTheListWasOpened() = runTest {
        val downloads = FakeAudioDownloads(bytesPerSurah = 700L)
        val viewModel =
            RecitersViewModel(reciters, downloads, ListenSettings(InMemoryPreferences()))
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        assertEquals(0L, viewModel.state.value.rows.single().downloadedBytes)

        downloads.downloadSurah("alafasy", 2)
        advanceUntilIdle()

        assertEquals(700L, viewModel.state.value.rows.single().downloadedBytes)
    }
}
