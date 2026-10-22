package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.player.PauseAfterAyah
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.fakes.FakeAudioDownloads
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A page whose ayahs depend on the print, like the real Mushaf prints do. */
private class PrintAwareQuranText(private val base: FakeQuranTextRepository) :
    QuranTextRepository by base {

    val requestedPrints = mutableListOf<ScriptId>()

    override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> {
        requestedPrints += script
        return if (script == ScriptId.IndoPak15) {
            listOf(AyahRef(2, 20), AyahRef(2, 21))
        } else {
            base.ayahsOnPage(page, script)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudioFeatureTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val quranText =
        PrintAwareQuranText(
            FakeQuranTextRepository(
                chapterList = listOf(Chapter(2, "", "Al-Baqarah", null, 286, "madinah", true)),
                pageByAyah = mutableMapOf(AyahRef(2, 10) to 5, AyahRef(2, 11) to 5),
            ),
        )

    private fun TestScope.settle() {
        advanceUntilIdle()
        runCurrent()
    }

    private fun TestScope.featureOn(settings: ListenSettings, player: FakeAudioPlayer) =
        AudioFeature(
            player = player,
            downloads = FakeAudioDownloads(),
            reciters = FakeReciterRepository(),
            quranText = quranText,
            settings = settings,
            scope = backgroundScope,
        )

    @Test
    fun repeatSpeedAndMemorisationOptionsSurviveARestart() = runTest {
        val settings = ListenSettings(InMemoryPreferences())
        val firstPlayer = FakeAudioPlayer()
        featureOn(settings, firstPlayer)
        settle()

        val chosen =
            PlaybackOptions(
                repeatEachAyah = 5,
                repeatRange = PlaybackOptions.INFINITE_REPEAT,
                speed = 0.75f,
                pauseAfterAyah = PauseAfterAyah.OneAndHalf,
                cumulative = true,
                stopOnTaskRemoved = false,
            )
        firstPlayer.setOptions(chosen)
        settle()

        // A new launch: a new player and a new feature on the same stored settings.
        val secondPlayer = FakeAudioPlayer()
        featureOn(settings, secondPlayer)
        settle()

        assertEquals(chosen, secondPlayer.options.value)
        assertEquals(chosen, settings.playbackOptions.first())
    }

    @Test
    fun theDefaultsApplyOnAFirstLaunch() = runTest {
        val player = FakeAudioPlayer()
        featureOn(ListenSettings(InMemoryPreferences()), player)
        settle()
        assertEquals(PlaybackOptions(), player.options.value)
    }

    @Test
    fun theBarMapsAPageToAyahsWithThePrintTheReaderChose() = runTest {
        val player = FakeAudioPlayer()
        val feature = featureOn(ListenSettings(InMemoryPreferences()), player)
        val barViewModel = feature.viewModelFactory.create(AudioBarViewModel::class.java)
        backgroundScope.launch { barViewModel.state.collect {} }
        settle()

        barViewModel.showPage(5)
        barViewModel.play()
        settle()
        assertEquals(AyahRef(2, 10), player.playCalls.last().second)

        feature.useScript(ScriptId.IndoPak15)
        barViewModel.showPage(5)
        barViewModel.play()
        settle()
        assertEquals(AyahRef(2, 20), player.playCalls.last().second)
        assertEquals(ScriptId.IndoPak15, quranText.requestedPrints.last())
    }
}
