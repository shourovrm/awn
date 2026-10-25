package app.awn.quran.search

import android.app.Application
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.ui.text.normalizeFallbackText
import app.awn.quran.feature.recite.DelayedRunner
import app.awn.quran.feature.recite.PreparedSpeech
import app.awn.quran.feature.recite.SpeechPreparation
import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.TrackerInput
import app.awn.quran.tracker.WordKeyLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric provides android.util.Log, which the failure path writes to.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun useTestMain() = Dispatchers.setMain(dispatcher)

    @After fun restoreMain() = Dispatchers.resetMain()

    // Surah 1 has one ayah whose phonemes are "abcdefghij"; the fake microphone recites it.
    private val reference =
        object : ReferencePhonemes {
            override fun forAyah(surah: Int, ayah: Int): List<ReferenceWord> =
                if (surah == 1 && ayah == 1) {
                    listOf(ReferenceWord(WordKeyLike(1, 1, 1), "abcdefghij".map { it.toString() }))
                } else {
                    emptyList()
                }
        }

    private val quranText =
        FakeQuranTextRepository(
            chapterList = listOf(Chapter(1, "الفاتحة", "Al-Fatihah", null, 7, "makkah", false)),
            wordsByAyah =
                mutableMapOf(
                    AyahRef(1, 1) to
                        listOf(
                            Word(WordKey(1, 1, 1), "بِسْمِ", null, null),
                            Word(WordKey(1, 1, 2), "ٱللَّهِ", null, null),
                        ),
                ),
            pageByAyah = mutableMapOf(AyahRef(1, 1) to 1),
        )

    private var preparationFails = false
    private var released = false

    private val speech =
        SpeechPreparation(
            executor = Runnable::run,
            delayedRunner =
                DelayedRunner { _, action ->
                    action.run()
                    released = true
                    ({})
                },
        ) {
            check(!preparationFails) { "The speech model is not on the phone." }
            object : PreparedSpeech {
                override val referencePhonemes = reference

                override fun newPhonemeSource(): PhonemeSource = SpellingSource("abcdefghij")

                override fun close() = Unit
            }
        }

    private val microphone = RecordedMicrophone(blockCount = 10)

    private fun viewModel(isReciteActive: Boolean = false) =
        VoiceSearchViewModel(
            speech = speech,
            openMicrophone = { microphone },
            quranText = quranText,
            script = ScriptId.V2,
            isReciteActive = { isReciteActive },
            listeningDispatcher = dispatcher,
        )

    @Test
    fun aRecitedAyahIsListedWithItsTextAndPage() =
        runTest(dispatcher) {
            val model = viewModel()
            val collector = launch { model.state.collect {} }

            model.startListening()
            advanceUntilIdle()

            val state = model.state.value
            assertEquals(VoicePhase.Finished, state.phase)
            val row = state.rows.single()
            assertEquals(AyahRef(1, 1), row.hit.ayah)
            assertEquals(normalizeFallbackText("بِسْمِ ٱللَّهِ"), row.hit.text)
            // The reference of this test has one word, so only the first word was recited.
            assertEquals(listOf(0 until normalizeFallbackText("بِسْمِ").length), row.hit.matches)
            assertEquals("Al-Fatihah", row.surahName)
            assertEquals(1, row.page)
            collector.cancel()
        }

    @Test
    fun theModelIsReleasedAfterListeningWhenReciteIsOff() =
        runTest(dispatcher) {
            viewModel().startListening()
            advanceUntilIdle()

            assertTrue(released)
        }

    @Test
    fun theModelStaysLoadedWhileReciteUsesIt() =
        runTest(dispatcher) {
            viewModel(isReciteActive = true).startListening()
            advanceUntilIdle()

            assertEquals(false, released)
        }

    @Test
    fun aModelThatCannotLoadShowsTheFailure() =
        runTest(dispatcher) {
            preparationFails = true
            val model = viewModel()
            val collector = launch { model.state.collect {} }

            model.startListening()
            advanceUntilIdle()

            assertEquals(VoicePhase.Failed, model.state.value.phase)
            collector.cancel()
        }

    @Test
    fun closingClearsTheMatches() =
        runTest(dispatcher) {
            val model = viewModel()
            val collector = launch { model.state.collect {} }
            model.startListening()
            advanceUntilIdle()

            model.close()
            advanceUntilIdle()

            assertEquals(VoiceSearchUiState(), model.state.value)
            assertTrue(microphone.stopped)
            collector.cancel()
        }

    private class RecordedMicrophone(private val blockCount: Int) : StoppableInput {
        private var given = 0
        var stopped = false
            private set

        override fun nextBlock(): FloatArray? {
            if (stopped || given == blockCount) return null
            given++
            return FloatArray(TrackerInput.SAMPLE_RATE_HZ / 10)
        }

        override fun stop() {
            stopped = true
        }
    }

    // Hears one letter of [text] per block, then nothing.
    private class SpellingSource(private val text: String) : PhonemeSource {
        private var position = 0

        override fun accept(block: FloatArray): List<PhonemeEvent> {
            if (position >= text.length) return emptyList()
            val symbol = text[position++].toString()
            return listOf(PhonemeEvent(symbol, onsetSeconds = 0f, probability = 1f))
        }

        override fun finish(): List<PhonemeEvent> = emptyList()

        override fun close() = Unit
    }
}
