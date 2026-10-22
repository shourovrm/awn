package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.player.AudioStatus
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.PlaybackState
import app.awn.quran.player.fakes.FakeAudioDownloads
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val alafasy = Reciter("alafasy", "Alafasy", "s{SSS}", "a{SSS}{AAA}", true, "alafasy")
    private val husary = Reciter("husary", "Husary", "s{SSS}", "a{SSS}{AAA}", true, "husary")
    private val reciters = FakeReciterRepository(reciterList = mutableListOf(alafasy, husary))

    /** Page 1 holds 1:6 and 1:7 and the start of surah 2, page 5 holds only surah 2. */
    private val quranText =
        FakeQuranTextRepository(
            chapterList =
                listOf(
                    Chapter(1, "", "Al-Fatihah", null, 7, "makkah", true),
                    Chapter(2, "", "Al-Baqarah", null, 286, "madinah", true),
                ),
            pageByAyah =
                mutableMapOf(
                    AyahRef(1, 6) to 1,
                    AyahRef(1, 7) to 1,
                    AyahRef(2, 1) to 1,
                    AyahRef(2, 10) to 5,
                    AyahRef(2, 11) to 5,
                ),
        )

    private val player = FakeAudioPlayer()
    private val settings = ListenSettings(InMemoryPreferences())

    /** Runs every task, including those of backgroundScope, which advanceUntilIdle skips. */
    private fun TestScope.settle() {
        advanceUntilIdle()
        runCurrent()
    }

    private fun TestScope.controller() = AudioController(player, settings, backgroundScope)

    private fun TestScope.barViewModel() =
        AudioBarViewModel(
                player,
                reciters,
                quranText,
                settings,
                controller(),
                MutableStateFlow(ScriptId.V2),
            )
            .also {
                backgroundScope.launch { it.state.collect {} }
            }

    @Test
    fun barIsIdleWithReciterNameThenActiveThenShowsOnlyNonDefaultValues() = runTest {
        val viewModel = barViewModel()
        settle()
        assertEquals(AudioBarState.Idle("Alafasy", hasError = false), viewModel.state.value)

        player.play("alafasy", AyahRef(2, 10), null)
        assertEquals(AudioBarState.Active(true, null, null), viewModel.state.value)

        player.setOptions(PlaybackOptions(repeatEachAyah = 3, speed = 1.25f))
        assertEquals(AudioBarState.Active(true, 3, 1.25f), viewModel.state.value)

        player.setOptions(PlaybackOptions(repeatRange = PlaybackOptions.INFINITE_REPEAT))
        assertEquals(
            AudioBarState.Active(true, PlaybackOptions.INFINITE_REPEAT, null),
            viewModel.state.value,
        )

        player.pause()
        assertEquals(false, (viewModel.state.value as AudioBarState.Active).isPlaying)
    }

    /**
     * Choosing another reciter from the page while audio plays goes on with the new voice from the
     * ayah that was playing, to the same end; it does not wait for the next Play.
     */
    @Test
    fun choosingAnotherReciterWhilePlayingGoesOnWithTheNewVoiceFromTheSameAyah() = runTest {
        barViewModel()
        val controller = controller()
        settle()
        controller.playRange(AyahRef(2, 10), AyahRef(2, 11))
        settle()

        settings.selectReciter("husary")
        settle()

        assertEquals(Triple("husary", AyahRef(2, 10), AyahRef(2, 11)), player.playCalls.last())
    }

    @Test
    fun choosingAnotherReciterWhilePausedStaysPaused() = runTest {
        barViewModel()
        val controller = controller()
        settle()
        controller.playRange(AyahRef(2, 10), null)
        settle()
        player.pause()
        val callsBefore = player.playCalls.size

        settings.selectReciter("husary")
        settle()

        assertEquals(callsBefore, player.playCalls.size)
        assertTrue(player.currentState.value is PlaybackState.Paused)
    }

    @Test
    fun choosingAReciterWhileIdleStartsNothing() = runTest {
        barViewModel()
        controller()
        settle()

        settings.selectReciter("husary")
        settle()

        assertTrue(player.playCalls.isEmpty())
    }

    @Test
    fun barShowsDownloadingBytesAndError() = runTest {
        val viewModel = barViewModel()
        player.currentState.value = PlaybackState.Downloading(2_100L, 5_400L)
        settle()
        assertEquals(AudioBarState.Downloading(2_100L, 5_400L), viewModel.state.value)

        player.currentState.value = PlaybackState.Error("x")
        settle()
        assertTrue((viewModel.state.value as AudioBarState.Idle).hasError)
    }

    @Test
    fun playOnAPageWithOneSurahStartsAtItsFirstAyah() = runTest {
        val viewModel = barViewModel()
        viewModel.showPage(5)
        viewModel.play()
        settle()

        assertEquals(listOf(Triple("alafasy", AyahRef(2, 10), null)), player.playCalls)
    }

    @Test
    fun playOnAPageWithTwoSurahsAsksWhereToStart() = runTest {
        val viewModel = barViewModel()
        viewModel.showPage(1)
        viewModel.play()
        settle()

        val choices = viewModel.startChoices.value
        assertEquals(listOf(AyahRef(1, 6), AyahRef(2, 1)), choices.map { it.ayah })
        assertEquals(listOf("Al-Fatihah", "Al-Baqarah"), choices.map { it.surahName })
        assertTrue(player.playCalls.isEmpty())

        viewModel.choose(choices[1])
        settle()

        assertEquals(AyahRef(2, 1), player.playCalls.single().second)
        assertTrue(viewModel.startChoices.value.isEmpty())
    }

    @Test
    fun playWhilePausedResumesInsteadOfRestarting() = runTest {
        val viewModel = barViewModel()
        viewModel.showPage(5)
        player.play("alafasy", AyahRef(2, 10), null)
        player.pause()

        viewModel.play()
        settle()

        assertEquals(1, player.playCalls.size)
        assertTrue(player.currentState.value is PlaybackState.Playing)
    }

    @Test
    fun repeatAndSpeedButtonsStepThroughTheirChoices() = runTest {
        val viewModel = barViewModel()

        viewModel.cycleRepeat()
        assertEquals(2, player.options.value.repeatEachAyah)
        repeat(4) { viewModel.cycleRepeat() }
        assertEquals(PlaybackOptions.INFINITE_REPEAT, player.options.value.repeatEachAyah)
        viewModel.cycleRepeat()
        assertEquals(1, player.options.value.repeatEachAyah)

        viewModel.cycleSpeed()
        assertEquals(1.25f, player.options.value.speed)
        viewModel.cycleSpeed()
        viewModel.cycleSpeed()
        assertEquals(0.75f, player.options.value.speed)
        viewModel.cycleSpeed()
        assertEquals(1f, player.options.value.speed)
    }

    @Test
    fun speedIsFormattedWithoutTrailingZeros() {
        assertEquals("1.25", formatSpeed(1.25f))
        assertEquals("1.5", formatSpeed(1.5f))
        assertEquals("0.75", formatSpeed(0.75f))
        assertEquals("1", formatSpeed(1f))
        assertEquals("1.15", formatSpeed(1.1500001f))
    }

    @Test
    fun panelRangeEndsAtTheLastAyahOfThePage() = runTest {
        val viewModel =
            PlayRangeViewModel(player, quranText, controller(), MutableStateFlow(ScriptId.V2))
                .also {
                    backgroundScope.launch { it.state.collect {} }
                }
        viewModel.load(AyahRef(1, 6), page = 1)
        settle()

        assertEquals(AyahRef(2, 1), viewModel.state.value.selection?.to)

        viewModel.setToSurah(2)
        assertEquals(AyahRef(2, 286), viewModel.state.value.selection?.to)

        viewModel.setFromSurah(2)
        viewModel.setFromAyah(300)
        assertEquals(AyahRef(2, 300), viewModel.state.value.selection?.to)
    }

    @Test
    fun panelPlayHonoursPlayOnlyThisRange() = runTest {
        val viewModel =
            PlayRangeViewModel(player, quranText, controller(), MutableStateFlow(ScriptId.V2))
        viewModel.load(AyahRef(2, 10), page = 5)
        settle()

        viewModel.play()
        settle()
        assertEquals(Triple("alafasy", AyahRef(2, 10), AyahRef(2, 11)), player.playCalls.last())

        viewModel.setOnlyRange(false)
        viewModel.play()
        settle()
        assertNull(player.playCalls.last().third)
    }

    @Test
    fun panelRepeatAndSpeedReachThePlayerAtOnce() = runTest {
        val viewModel =
            PlayRangeViewModel(player, quranText, controller(), MutableStateFlow(ScriptId.V2))

        viewModel.setRepeatEachAyah(5)
        viewModel.setRepeatRange(PlaybackOptions.INFINITE_REPEAT)
        viewModel.setSpeed(9f)

        assertEquals(
            PlaybackOptions(5, PlaybackOptions.INFINITE_REPEAT, PlaybackOptions.MAX_SPEED),
            player.options.value,
        )
    }

    @Test
    fun controllerPlaysFromAnAyahWithTheSelectedReciter() = runTest {
        settings.selectReciter("husary")

        controller().playFrom(AyahRef(2, 255))
        settle()

        assertEquals(Triple("husary", AyahRef(2, 255), null), player.playCalls.single())
    }

    @Test
    fun reciterGroupsAreExclusiveAndSearchable() {
        val third = Reciter("minshawi", "Minshawi", "s", "a", true, null)
        val all = listOf(alafasy, husary, third)

        val groups = groupReciters(all, setOf("husary"), setOf("husary", "alafasy"), "")
        assertEquals(listOf(husary), groups.downloadedForPage)
        assertEquals(listOf(alafasy), groups.withDownloads)
        assertEquals(listOf(third), groups.all)

        val searched = groupReciters(all, setOf("husary"), setOf("alafasy"), " mIn ")
        assertEquals(emptyList<Reciter>(), searched.downloadedForPage)
        assertEquals(listOf(third), searched.all)
    }

    @Test
    fun reciterSheetMarksPageDownloadsAndSelects() = runTest {
        val downloads = FakeAudioDownloads()
        downloads.download("husary", listOf(AyahRef(2, 10), AyahRef(2, 11)))
        val viewModel =
            ReciterSheetViewModel(
                    reciters,
                    downloads,
                    quranText,
                    settings,
                    MutableStateFlow(ScriptId.V2),
                )
                .also {
                    backgroundScope.launch { it.state.collect {} }
                }

        viewModel.load(page = 5)
        settle()
        assertEquals(listOf(husary), viewModel.state.value.groups.downloadedForPage)
        assertEquals(listOf(alafasy), viewModel.state.value.groups.all)

        viewModel.select("husary")
        settle()
        assertEquals("husary", viewModel.state.value.selectedId)
    }

    @Test
    fun settingsDownloadAmountAndTotalSpace() = runTest {
        val downloads = FakeAudioDownloads(bytesPerAyah = 10L)
        downloads.download("alafasy", listOf(AyahRef(1, 1), AyahRef(1, 2)))
        downloads.download("husary", listOf(AyahRef(1, 1)))
        val viewModel =
            AudioSettingsViewModel(reciters, downloads, settings, player).also {
                backgroundScope.launch { it.state.collect {} }
            }
        settle()

        assertEquals(30L, viewModel.state.value.usedBytes)
        assertEquals("Alafasy", viewModel.state.value.reciterName)

        viewModel.setDownloadAmount(app.awn.quran.player.DownloadAmount.Juz)
        settle()
        assertEquals(app.awn.quran.player.DownloadAmount.Juz, settings.downloadAmount.first())
    }

    @Test
    fun managerShowsSizeBeforeDownloadingThenDownloadsAndDeletes() = runTest {
        val downloads = FakeAudioDownloads(bytesPerAyah = 10L)
        downloads.estimate = 4_321L
        val text =
            FakeQuranTextRepository(
                chapterList = listOf(Chapter(1, "", "Al-Fatihah", null, 7, "makkah", true)),
            )
        val viewModel = DownloadsManagerViewModel(downloads, reciters, text, backgroundScope)
        val scope = ItemScope.Surah(1)
        val key = DownloadsManagerViewModel.itemKey("alafasy", scope)

        viewModel.load("alafasy", scope)
        settle()
        assertEquals(4_321L, viewModel.items.value.getValue(key).estimateBytes)
        assertEquals(AudioStatus(0L, 0, 7), viewModel.items.value.getValue(key).status)

        viewModel.download("alafasy", scope)
        settle()
        val afterDownload = viewModel.items.value.getValue(key)
        assertTrue(afterDownload.status!!.complete)
        assertNull(afterDownload.progress)

        viewModel.delete("alafasy", scope)
        settle()
        assertEquals(0, viewModel.items.value.getValue(key).status!!.presentAyahs)
    }

    @Test
    fun barAsksBeforeABigDownloadAndNamesAFailureCause() = runTest {
        val viewModel = barViewModel()
        player.prompt.value = app.awn.quran.player.DownloadPrompt(32_000_000L, metered = false)
        settle()
        assertEquals(AudioBarState.Confirm(32_000_000L, false), viewModel.state.value)
        player.prompt.value = null

        val problem = app.awn.quran.player.DownloadProblem.NotEnoughSpace(120_000_000L, 40_000_000L)
        player.currentState.value = PlaybackState.Error("x", problem)
        settle()
        assertEquals(problem, (viewModel.state.value as AudioBarState.Idle).problem)
    }

    @Test
    fun barShowsTheDrillStep() = runTest {
        val viewModel = barViewModel()
        player.currentState.value =
            PlaybackState.Playing(
                "alafasy",
                AyahRef(2, 10),
                null,
                0L,
                null,
                app.awn.quran.player.PlayStep(3, 7),
            )
        settle()
        assertEquals(
            app.awn.quran.player.PlayStep(3, 7),
            (viewModel.state.value as AudioBarState.Active).step,
        )
    }

    @Test
    fun positionTicksDuringPlaybackDoNotChangeTheBarState() = runTest {
        val viewModel = barViewModel()
        val seen = mutableListOf<AudioBarState>()
        backgroundScope.launch { viewModel.state.collect { seen += it } }
        player.currentState.value = PlaybackState.Playing("alafasy", AyahRef(2, 10), 1, 0L, 5_000L)
        settle()
        val countBefore = seen.size

        // The player publishes every 50 ms; the bar must not rebuild for any of them.
        for (tick in 1..100) {
            player.currentState.value =
                PlaybackState.Playing("alafasy", AyahRef(2, 10), tick % 3, tick * 50L, 5_000L)
        }
        settle()

        assertEquals(countBefore, seen.size)
    }
}
