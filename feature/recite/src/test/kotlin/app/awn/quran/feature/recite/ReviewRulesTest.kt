package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.player.fakes.FakeAudioPlayer
import app.awn.quran.tracker.DurationCheck
import app.awn.quran.tracker.DurationVerdict
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRulesTest {

    private val keys = listOf(WordKey(1, 1, 1), WordKey(1, 1, 2), WordKey(1, 2, 1))

    @Test
    fun cleanWordsAreNotListed() {
        val reviews =
            ReviewRules.reviewsByAyah(keys, List(3) { WordState.Confirmed }, emptySet(), emptyMap())

        assertTrue(reviews.isEmpty())
    }

    @Test
    fun skippedAndWrongWordsAreGroupedByAyahInOrder() {
        val states = listOf(WordState.Wrong, WordState.Wrong, WordState.Wrong)

        val reviews = ReviewRules.reviewsByAyah(keys, states, setOf(1), emptyMap())

        assertEquals(
            mapOf(
                AyahRef(1, 1) to
                    listOf(
                        WordReview(keys[0], listOf(ReviewFinding.WrongWord)),
                        WordReview(keys[1], listOf(ReviewFinding.Skipped)),
                    ),
                AyahRef(1, 2) to listOf(WordReview(keys[2], listOf(ReviewFinding.WrongWord))),
            ),
            reviews,
        )
    }

    @Test
    fun findingsAreOrderedVerdictThenPronunciationThenDuration() {
        val substitution =
            TrackerEvent.Substitution(wordIndex = 0, phonemeIndex = 1, heard = "س", expected = "ص")
        val duration = DurationCheck(0, "ا", "natural", 0.28f, 0.6f, DurationVerdict.Underheld)

        val reviews =
            ReviewRules.reviewsByAyah(
                keys,
                listOf(WordState.Wrong, WordState.Confirmed, WordState.Confirmed),
                emptySet(),
                mapOf(keys[0] to listOf(duration)),
                mapOf(keys[0] to listOf(substitution)),
            )

        assertEquals(
            listOf(
                ReviewFinding.WrongWord,
                ReviewFinding.Pronunciation(substitution),
                ReviewFinding.Duration(duration),
            ),
            reviews.getValue(AyahRef(1, 1)).single().findings,
        )
    }

    @Test
    fun anOkDurationAloneDoesNotFlagAWord() {
        val duration = DurationCheck(0, "ا", "natural", 0.6f, 0.6f, DurationVerdict.Ok)

        val reviews =
            ReviewRules.reviewsByAyah(
                keys,
                List(3) { WordState.Confirmed },
                emptySet(),
                mapOf(keys[0] to listOf(duration)),
            )

        assertTrue(reviews.isEmpty())
    }

    @Test
    fun aBadDurationFlagsAConfirmedWord() {
        val duration = DurationCheck(0, "ا", "natural", 1.2f, 0.6f, DurationVerdict.Overheld)

        val reviews =
            ReviewRules.reviewsByAyah(
                keys,
                List(3) { WordState.Confirmed },
                emptySet(),
                mapOf(keys[0] to listOf(duration)),
            )

        assertEquals(
            listOf(ReviewFinding.Duration(duration)),
            reviews.getValue(AyahRef(1, 1)).single().findings,
        )
    }

    @Test
    fun playingAFlaggedWordCallsThePlayerWithReciterAyahAndPosition() = runTest {
        val player = FakeAudioPlayer()

        playWord(player, "husary", WordKey(2, 255, 4))

        assertEquals(listOf(Triple("husary", AyahRef(2, 255), 4)), player.wordCalls)
    }
}
