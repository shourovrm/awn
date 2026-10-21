package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.WordKeyLike
import app.awn.quran.tracker.WordState
import app.awn.quran.tracker.fakes.FakeReferencePhonemes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Hands out one list of phoneme symbols per block of audio, as the model does. */
private class ScriptedPhonemeSource(private val batches: List<List<String>>) : PhonemeSource {
    private var next = 0

    override fun accept(block: FloatArray): List<PhonemeEvent> =
        batches
            .getOrElse(next++) { emptyList() }
            .mapIndexed { index, symbol ->
                PhonemeEvent(symbol, next * 0.1f + index * 0.04f, -1f)
            }

    override fun finish(): List<PhonemeEvent> = emptyList()

    override fun close() = Unit
}

/**
 * The phone test coloured words while nobody recited. These tests run the real tracker behind the
 * session: a few stray phonemes, as ambient sound makes the model emit, must leave every word
 * unmarked, and real recitation after them must still be followed.
 */
class ReciteNoiseTest {

    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private val wordTexts = listOf("abcdef", "ghijkl", "mnopqr", "stuvwx")

    private fun reference() =
        FakeReferencePhonemes(
            mutableMapOf(
                (1 to 1) to listOf(word(1, 1, 1, wordTexts[0]), word(1, 1, 2, wordTexts[1])),
                (1 to 2) to listOf(word(1, 2, 1, wordTexts[2])),
                (1 to 3) to listOf(word(1, 3, 1, wordTexts[3])),
            ),
        )

    private fun word(surah: Int, ayah: Int, position: Int, phonemes: String) =
        ReferenceWord(WordKeyLike(surah, ayah, position), phonemes.map { it.toString() })

    private fun fixtureHearing(batches: List<List<String>>) =
        ControllerFixture(
            folder.root,
            inputBlocks = batches.size,
            phonemeSource = { ScriptedPhonemeSource(batches) },
            fakeTrackers = null,
            reference = reference(),
        )

    @Test
    fun strayPhonemesColourNoWord() = runTest {
        val fixture = fixtureHearing(listOf(listOf("a", "x"), listOf("c"), listOf("q", "g")))
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.session.start()

        val states = fixture.controller.session.wordStates.value
        // The three words of the page plus the word of the next page, appended once phonemes came.
        assertEquals(4, states.size)
        assertTrue("states $states", states.values.all { it == WordState.Neutral })
    }

    @Test
    fun recitationAfterStrayPhonemesIsStillFollowed() = runTest {
        val fixture =
            fixtureHearing(
                listOf(
                    listOf("x", "z"),
                    listOf("a", "b", "c"),
                    listOf("d", "e", "f"),
                    listOf("g", "h", "i"),
                ),
            )
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.session.start()

        val states = fixture.controller.session.wordStates.value
        assertEquals(WordState.Confirmed, states[fixture.first])
    }
}
