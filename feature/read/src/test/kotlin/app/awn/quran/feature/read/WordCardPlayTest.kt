package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.InstalledPack
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.core.data.fakes.InMemoryWantedPackStore
import app.awn.quran.core.data.fakes.RecordingPackWorkScheduler
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.quranfont.AyahWord
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.quran.WordAudioOffer
import app.awn.quran.feature.read.quran.changedTimingsOnly
import app.awn.quran.feature.read.quran.shouldDownloadWordAudio
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The play button of the word card: a tap on a word only opens the card, sound is the button's. */
class WordCardPlayTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val reciters = FakeReciterRepository()
    private val viewModel by lazy {
        QuranViewModel(fixture.dependencies.copy(reciters = reciters), wordLanguage = "en")
    }
    private val firstAyah = AyahRef(1, 1)
    private val secondWordKey = WordKey(1, 1, 2)

    private fun secondWord() =
        AyahWord(
            key = secondWordKey,
            arabic = ReadFixture.SECOND_WORD,
            meaning = "(of) Allah",
            transliteration = "allahi",
        )

    private fun reciterHasWordTimings() {
        reciters.audio[ReadDependencies.DEFAULT_RECITER_ID to firstAyah] =
            AyahAudio(
                firstAyah,
                "https://example.org/1.mp3",
                4000,
                listOf(WordTiming(1, 0, 900), WordTiming(2, 900, 1800)),
            )
    }

    @Test
    fun tappingAWordOpensTheCardWithoutPlayingIt() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, secondWord())

        assertTrue(fixture.player.wordCalls.isEmpty())
        assertEquals(secondWordKey, checkNotNull(viewModel.state.value.wordPopup).word.key)
    }

    @Test
    fun theButtonIsOfferedWhenTheReciterHasWordTimings() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, secondWord())

        assertTrue(checkNotNull(viewModel.state.value.wordPopup).canPlay)
    }

    @Test
    fun theButtonIsHiddenWhenTheReciterHasNoWordTimings() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, secondWord())

        assertFalse(checkNotNull(viewModel.state.value.wordPopup).canPlay)
    }

    @Test
    fun theButtonPlaysTheSurahAyahAndPositionOfTheCard() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, secondWord())

        viewModel.playPopupWord()

        assertEquals(
            listOf(Triple(ReadDependencies.DEFAULT_RECITER_ID, firstAyah, 2)),
            fixture.player.wordCalls,
        )
    }

    @Test
    fun theButtonShowsStopWhileTheWordPlaysAndPlayWhenItEnds() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, secondWord())

        viewModel.playPopupWord()
        assertTrue(viewModel.state.value.isPopupWordPlaying)

        fixture.player.currentState.value = PlaybackState.Idle
        assertFalse(viewModel.state.value.isPopupWordPlaying)
    }

    @Test
    fun stopOnTheButtonStopsThePlayer() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, secondWord())
        viewModel.playPopupWord()

        viewModel.stopPopupWord()

        assertEquals(1, fixture.player.stopCalls)
        assertFalse(viewModel.state.value.isPopupWordPlaying)
    }

    @Test
    fun aFailedPlayReturnsTheButtonToPlay() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, secondWord())
        viewModel.playPopupWord()

        fixture.player.currentState.value = PlaybackState.Error("offline")

        assertFalse(viewModel.state.value.isPopupWordPlaying)
    }

    // Word audio is a download per reciter: the card offers it, and it also starts by itself.

    private val timingPackBytes = 3_162_112L
    private val scheduler = RecordingPackWorkScheduler()
    private val selectedReciter = MutableStateFlow(ReadDependencies.DEFAULT_RECITER_ID)
    private val chosenWordReciter = MutableStateFlow<String?>(null)

    private fun reciter(id: String, hasWordTimings: Boolean = true) =
        Reciter(
            id,
            id,
            "https://example.org/{SSS}.mp3",
            "https://example.org/{SSS}{AAA}.mp3",
            hasWordTimings,
            id,
        )

    private fun timingPackItem(id: String, isInstalled: Boolean = false) =
        fakeCatalogueItem(
            id,
            group = CatalogueGroup.WordAudio,
            language = null,
            downloadBytes = timingPackBytes,
            isInstalled = isInstalled,
        )

    private fun viewModelOnNetwork(isMetered: Boolean): QuranViewModel =
        QuranViewModel(
            fixture.dependencies.copy(
                reciters = reciters,
                downloads = PackDownloads(InMemoryWantedPackStore(), scheduler),
                selectedReciterId = selectedReciter,
                wordReciterId = chosenWordReciter,
                isNetworkMetered = { isMetered },
            ),
            wordLanguage = "en",
        )

    private fun reciterWithTimingPackNotInstalled(id: String = "alafasy") {
        reciters.reciterList += reciter(id)
        fixture.packs.catalogueItems += timingPackItem(id)
    }

    private fun QuranViewModel.card() = checkNotNull(state.value.wordPopup)

    @Test
    fun anUnmeteredNetworkDownloadsTheWordAudioOnceWhenTheScreenOpens() = runTest {
        reciterWithTimingPackNotInstalled()
        chosenWordReciter.value = "alafasy"
        val model = viewModelOnNetwork(isMetered = false)
        model.open(QuranTarget.Page(1))

        model.onWordTapped(firstAyah, secondWord())
        model.onWordTapped(firstAyah, secondWord())

        assertEquals(listOf("alafasy"), scheduler.enqueued)
    }

    @Test
    fun aMeteredNetworkOffersTheDownloadOnTheCardAndStartsOnlyOnATap() = runTest {
        reciterWithTimingPackNotInstalled()
        val model = viewModelOnNetwork(isMetered = true)
        model.open(QuranTarget.Page(1))

        model.onWordTapped(firstAyah, secondWord())

        assertTrue(scheduler.enqueued.isEmpty())
        assertFalse(model.card().canPlay)
        assertEquals(WordAudioOffer.Download(timingPackBytes), model.card().audioOffer)

        model.downloadWordAudio()

        assertEquals(listOf("alafasy"), scheduler.enqueued)
        assertEquals(WordAudioOffer.Downloading(0f), model.card().audioOffer)
    }

    @Test
    fun theCardShowsProgressThenPlayWhenTheWordAudioArrives() = runTest {
        reciterWithTimingPackNotInstalled()
        val model = viewModelOnNetwork(isMetered = true)
        model.open(QuranTarget.Page(1))
        model.onWordTapped(firstAyah, secondWord())
        model.downloadWordAudio()

        scheduler.transferStates.value = mapOf("alafasy" to PackTransfer.Running(0.4f))
        assertEquals(WordAudioOffer.Downloading(0.4f), model.card().audioOffer)

        reciterHasWordTimings()
        scheduler.transferStates.value = emptyMap()
        fixture.packs.install(timingPackItem("alafasy").entry) {}

        assertTrue(model.card().canPlay)
        assertEquals(null, model.card().audioOffer)
    }

    @Test
    fun aFailedDownloadIsNamedOnTheCardAndCanBeTriedAgain() = runTest {
        reciterWithTimingPackNotInstalled()
        val model = viewModelOnNetwork(isMetered = true)
        model.open(QuranTarget.Page(1))
        model.onWordTapped(firstAyah, secondWord())

        val failure = PackTransfer.Failed(PackFailureReason.NoInternet)
        scheduler.transferStates.value = mapOf("alafasy" to failure)

        assertEquals(WordAudioOffer.Failed(failure, timingPackBytes), model.card().audioOffer)
    }

    @Test
    fun aReciterWithoutWordTimingsOffersNothingAndDownloadsNothing() = runTest {
        // Basfar has a timing pack that marks ayahs only, so the reciter is not a word reciter.
        reciters.reciterList += reciter("basfar", hasWordTimings = false)
        selectedReciter.value = "basfar"
        val model = viewModelOnNetwork(isMetered = false)
        model.open(QuranTarget.Page(1))

        model.onWordTapped(firstAyah, secondWord())

        assertTrue(scheduler.enqueued.isEmpty())
        assertFalse(model.card().canPlay)
        assertEquals(null, model.card().audioOffer)
    }

    @Test
    fun aWordReciterTheUserDidNotChooseIsOfferedButNeverDownloadedByItself() = runTest {
        // Basfar cannot play words, so the first reciter with word timings is the word reciter.
        reciters.reciterList += reciter("basfar", hasWordTimings = false)
        reciterWithTimingPackNotInstalled("husary")
        selectedReciter.value = "basfar"
        val model = viewModelOnNetwork(isMetered = false)

        model.open(QuranTarget.Page(1))
        model.onWordTapped(firstAyah, secondWord())

        assertTrue(scheduler.enqueued.isEmpty())
        assertEquals(WordAudioOffer.Download(timingPackBytes), model.card().audioOffer)
    }

    @Test
    fun wordAudioIsNotStartedAgainForAnItemThatIsQueuedInstalledOrAlreadyAsked() {
        val notInstalled = CatalogueRow(timingPackItem("a"), null)
        val queued = notInstalled.copy(transfer = PackTransfer.Waiting)
        val installed = CatalogueRow(timingPackItem("a", true), null)

        assertTrue(
            shouldDownloadWordAudio(
                notInstalled,
                isChosenByUser = true,
                isNetworkMetered = false,
                emptySet(),
            ),
        )
        assertFalse(
            shouldDownloadWordAudio(
                notInstalled,
                isChosenByUser = false,
                isNetworkMetered = false,
                emptySet(),
            ),
        )
        assertFalse(
            shouldDownloadWordAudio(
                notInstalled,
                isChosenByUser = true,
                isNetworkMetered = true,
                emptySet(),
            ),
        )
        assertFalse(
            shouldDownloadWordAudio(
                queued,
                isChosenByUser = true,
                isNetworkMetered = false,
                emptySet(),
            ),
        )
        assertFalse(
            shouldDownloadWordAudio(
                installed,
                isChosenByUser = true,
                isNetworkMetered = false,
                emptySet(),
            ),
        )
        assertFalse(
            shouldDownloadWordAudio(
                notInstalled,
                isChosenByUser = true,
                isNetworkMetered = false,
                setOf("a"),
            ),
        )
    }

    @Test
    fun installingTimingsDoesNotCountAsAChangeToThePages() {
        val translation = InstalledPack("en_saheeh", "translation", 1, 10L)
        val timing = InstalledPack("alafasy", "timing", 1, 20L)

        assertTrue(changedTimingsOnly(listOf(translation), listOf(translation, timing)))
        assertFalse(changedTimingsOnly(listOf(timing), listOf(timing, translation)))
        assertFalse(changedTimingsOnly(listOf(translation), listOf(translation)))
    }
}
