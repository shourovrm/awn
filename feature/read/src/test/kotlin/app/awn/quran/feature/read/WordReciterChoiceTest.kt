package app.awn.quran.feature.read

import app.awn.quran.core.data.DEFAULT_WORD_RECITER_ID
import app.awn.quran.feature.read.quran.wordReciterFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The user (2026-10-22): the reciter they listen to has no word timings, so the word card lost its
 * play button. Words get their own reciter setting (Settings > Audio, "Word audio"), listing only
 * reciters with word timings. [wordReciterFor] picks the reciter whose voice plays a single word.
 */
class WordReciterChoiceTest {

    private val withTimings = listOf("alafasy", "husary")

    @Test
    fun theWordReciterTheUserChoseIsUsed() {
        assertEquals("husary", wordReciterFor(chosen = "husary", listening = "sudais", withTimings))
    }

    @Test
    fun withoutAChoiceHusaryMuallimIsUsedEvenWhenTheListeningReciterHasTimings() {
        val withMuallim = withTimings + DEFAULT_WORD_RECITER_ID

        assertEquals(
            "husary_muallim",
            wordReciterFor(chosen = null, listening = "alafasy", withMuallim),
        )
    }

    @Test
    fun aChoiceBeatsTheDefaultReciter() {
        val withMuallim = withTimings + DEFAULT_WORD_RECITER_ID

        assertEquals(
            "alafasy",
            wordReciterFor(chosen = "alafasy", listening = "husary", withMuallim),
        )
    }

    @Test
    fun withoutAChoiceAndNoDefaultReciterTheListeningReciterIsUsedWhenItHasTimings() {
        assertEquals("alafasy", wordReciterFor(chosen = null, listening = "alafasy", withTimings))
    }

    @Test
    fun withoutAChoiceAndAListeningReciterWithoutTimingsTheFirstWithTimingsIsUsed() {
        assertEquals("alafasy", wordReciterFor(chosen = null, listening = "sudais", withTimings))
    }

    @Test
    fun aChoiceThatLostItsTimingsFallsBackLikeNoChoice() {
        assertEquals(
            "alafasy",
            wordReciterFor(chosen = "minshawi", listening = "sudais", withTimings),
        )
    }

    @Test
    fun noReciterWithTimingsMeansNoWordAudio() {
        assertNull(wordReciterFor(chosen = null, listening = "sudais", withTimings = emptyList()))
    }
}
