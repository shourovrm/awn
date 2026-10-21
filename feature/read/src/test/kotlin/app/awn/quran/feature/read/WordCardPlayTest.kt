package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.player.PlaybackState
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
}
