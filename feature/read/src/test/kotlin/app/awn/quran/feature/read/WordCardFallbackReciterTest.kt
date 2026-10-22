package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.WordTiming
import app.awn.quran.core.data.fakes.FakeReciterRepository
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Root cause of the missing play button (2026-10-22): a listening reciter whose timings mark ayahs
 * only (Basfar) or that has none (Raad al-Kurdi) made WordAudio.stateOf answer Unavailable, so the
 * card showed neither the play button nor a download. The card now plays the word with the word
 * reciter: the one chosen in Settings > Audio, else the first reciter with word timings.
 */
class WordCardFallbackReciterTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val reciters = FakeReciterRepository()
    private val selectedReciter = MutableStateFlow("basfar")
    private val chosenWordReciter = MutableStateFlow<String?>(null)
    private val firstAyah = AyahRef(1, 1)

    private val viewModel by lazy {
        QuranViewModel(
            fixture.dependencies.copy(
                reciters = reciters,
                selectedReciterId = selectedReciter,
                wordReciterId = chosenWordReciter,
            ),
            wordLanguage = "en",
        )
    }

    private fun reciter(id: String, hasWordTimings: Boolean) =
        Reciter(
            id,
            id,
            "https://example.org/{SSS}.mp3",
            "https://example.org/{SSS}{AAA}.mp3",
            hasWordTimings,
            id,
        )

    private fun secondWord() =
        AyahWord(
            key = WordKey(1, 1, 2),
            arabic = ReadFixture.SECOND_WORD,
            meaning = "(of) Allah",
            transliteration = "allahi",
        )

    private fun alafasyHasWordTimingsOnThePhone() {
        reciters.reciterList += reciter("basfar", hasWordTimings = false)
        reciters.reciterList += reciter(ReadDependencies.DEFAULT_RECITER_ID, hasWordTimings = true)
        reciters.audio[ReadDependencies.DEFAULT_RECITER_ID to firstAyah] =
            AyahAudio(
                firstAyah,
                "https://example.org/1.mp3",
                4000,
                listOf(WordTiming(2, 900, 1800)),
            )
    }

    @Test
    fun theWordReciterChosenInSettingsPlaysTheWord() = runTest {
        alafasyHasWordTimingsOnThePhone()
        reciters.reciterList += reciter("husary", hasWordTimings = true)
        reciters.audio["husary" to firstAyah] =
            AyahAudio(firstAyah, "https://example.org/h1.mp3", 4000, listOf(WordTiming(2, 0, 800)))
        chosenWordReciter.value = "husary"
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, secondWord())

        viewModel.playPopupWord()

        assertEquals(listOf(Triple("husary", firstAyah, 2)), fixture.player.wordCalls)
    }

    @Test
    fun aReciterWithoutWordTimingsStillGetsThePlayButton() = runTest {
        alafasyHasWordTimingsOnThePhone()
        viewModel.open(QuranTarget.Page(1))

        viewModel.onWordTapped(firstAyah, secondWord())

        assertTrue(checkNotNull(viewModel.state.value.wordPopup).canPlay)
    }

    @Test
    fun theButtonPlaysTheWordWithTheDefaultReciter() = runTest {
        alafasyHasWordTimingsOnThePhone()
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, secondWord())

        viewModel.playPopupWord()

        assertEquals(
            listOf(Triple(ReadDependencies.DEFAULT_RECITER_ID, firstAyah, 2)),
            fixture.player.wordCalls,
        )
    }
}
