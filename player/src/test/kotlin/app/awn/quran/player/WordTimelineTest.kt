package app.awn.quran.player

import app.awn.quran.core.data.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The timings below are rows of `awn-data/timings/alafasy.sqlite` (`segment`, milliseconds from the
 * start of the ayah file), so these tests run against the shape of the real packs: words that
 * touch, a 40 ms word and a long pause inside an ayah.
 */
class WordTimelineTest {

    /** Alafasy 1:2, four words, each starting 10 ms after the previous one ended. */
    private val alafasy1v2 =
        WordTimeline(
            listOf(
                WordTiming(1, 80, 960),
                WordTiming(2, 970, 1800),
                WordTiming(3, 1810, 2460),
                WordTiming(4, 2470, 5140),
            ),
        )

    /** Alafasy 2:142, words 11 to 13: word 12 lasts 40 ms. */
    private val alafasy2v142 =
        WordTimeline(
            listOf(
                WordTiming(11, 12280, 13870),
                WordTiming(12, 13880, 13920),
                WordTiming(13, 13930, 15210),
            ),
        )

    /** Alafasy 5:106, words 15 to 17: the reciter pauses from 17.3 s to 21.97 s. */
    private val alafasy5v106 =
        WordTimeline(
            listOf(
                WordTiming(15, 15240, 17300),
                WordTiming(16, 21970, 22390),
                WordTiming(17, 22400, 23640),
            ),
        )

    @Test
    fun nothingIsLitBeforeTheFirstWordStarts() {
        assertNull(alafasy1v2.wordPositionAt(0))
        assertNull(alafasy1v2.wordPositionAt(79))
        assertEquals(1, alafasy1v2.wordPositionAt(80))
    }

    @Test
    fun aWordIsLitFromItsStartThroughItsEnd() {
        assertEquals(1, alafasy1v2.wordPositionAt(960))
        assertEquals(2, alafasy1v2.wordPositionAt(970))
        assertEquals(2, alafasy1v2.wordPositionAt(1_800))
        assertEquals(4, alafasy1v2.wordPositionAt(5_000))
    }

    @Test
    fun theShortGapBetweenWordsKeepsThePreviousWordLit() {
        assertEquals(1, alafasy1v2.wordPositionAt(965))
        assertEquals(2, alafasy1v2.wordPositionAt(1_805))
    }

    @Test
    fun aFortyMillisecondWordIsFoundAtTheStepsPlaybackPublishes() {
        assertEquals(11, alafasy2v142.wordPositionAt(13_850))
        assertEquals(12, alafasy2v142.wordPositionAt(13_900))
        assertEquals(13, alafasy2v142.wordPositionAt(13_950))
    }

    @Test
    fun aLongPauseLightsNothingAfterTheHoldTime() {
        assertEquals(15, alafasy5v106.wordPositionAt(17_300))
        assertEquals(15, alafasy5v106.wordPositionAt(17_300 + WordTimeline.HOLD_AFTER_END_MS))
        assertNull(alafasy5v106.wordPositionAt(17_300 + WordTimeline.HOLD_AFTER_END_MS + 1))
        assertNull(alafasy5v106.wordPositionAt(20_000))
        assertEquals(16, alafasy5v106.wordPositionAt(21_970))
    }

    @Test
    fun theLastWordGoesDarkAfterTheHoldTime() {
        assertEquals(4, alafasy1v2.wordPositionAt(5_140 + WordTimeline.HOLD_AFTER_END_MS))
        assertNull(alafasy1v2.wordPositionAt(5_140 + WordTimeline.HOLD_AFTER_END_MS + 1))
    }

    @Test
    fun anAyahWithoutTimingsLightsNothing() {
        val timeline = WordTimeline(emptyList())
        assertNull(timeline.wordPositionAt(1_000))
    }

    @Test
    fun wordsGivenOutOfOrderAreSortedByStart() {
        val timeline = WordTimeline(listOf(WordTiming(2, 500, 900), WordTiming(1, 100, 400)))
        assertEquals(1, timeline.wordPositionAt(450))
        assertEquals(2, timeline.wordPositionAt(600))
    }
}
