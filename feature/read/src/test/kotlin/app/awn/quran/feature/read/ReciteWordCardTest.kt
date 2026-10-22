package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.tracker.WordProblem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The card that opens when the user taps a word that was recited wrongly or unclearly. */
class ReciteWordCardTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val reciters = FakeReciterRepository()
    private val viewModel by lazy {
        QuranViewModel(fixture.dependencies.copy(reciters = reciters), wordLanguage = "en")
    }
    private val firstAyah = AyahRef(1, 1)
    private val firstKey = WordKey(1, 1, 1)

    private fun aWord() =
        AyahWord(
            key = firstKey,
            arabic = ReadFixture.FIRST_WORD,
            meaning = "In (the) name",
            transliteration = "bismi",
        )

    private fun reciterHasWordTimings() {
        reciters.audio[ReadDependencies.DEFAULT_RECITER_ID to firstAyah] =
            AyahAudio(firstAyah, "https://example.org/1.mp3", 4000, listOf(WordTiming(1, 0, 900)))
    }

    @Test
    fun tappingAProblemWordOpensTheCardWithItsProblemAndDoesNotPlayByItself() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, aWord(), WordProblem.Skipped)

        val popup = checkNotNull(viewModel.state.value.wordPopup)
        assertEquals(WordProblem.Skipped, popup.problem)
        assertTrue(fixture.player.wordCalls.isEmpty())
    }

    @Test
    fun theCardOffersPlayWhenTheReciterHasWordTimings() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, aWord(), WordProblem.Mispronounced)

        assertTrue(checkNotNull(viewModel.state.value.wordPopup).canPlay)
    }

    @Test
    fun theCardOffersNoPlayWithoutWordTimings() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, aWord(), WordProblem.Mispronounced)

        assertFalse(checkNotNull(viewModel.state.value.wordPopup).canPlay)
    }

    @Test
    fun playingFromTheCardPlaysThatWordOfTheReciter() = runTest {
        reciterHasWordTimings()
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, aWord(), WordProblem.DifferentWord)

        viewModel.playPopupWord()

        assertEquals(
            listOf(Triple(ReadDependencies.DEFAULT_RECITER_ID, firstAyah, 1)),
            fixture.player.wordCalls,
        )
    }

    @Test
    fun aTapOutsideClosesTheCard() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, aWord(), WordProblem.Skipped)
        assertNotNull(viewModel.state.value.wordPopup)

        viewModel.onPageTapped()

        assertNull(viewModel.state.value.wordPopup)
    }

    @Test
    fun theTapThatFlagsAWordOnThePageDoesNotToggleTheBars() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val barsBefore = viewModel.state.value.areBarsVisible

        // The page reports a word tap and then the same tap as a page tap.
        viewModel.ignoreNextPageTap()
        viewModel.onPageTapped()

        assertEquals(barsBefore, viewModel.state.value.areBarsVisible)
    }
}
