package app.awn.quran.feature.learn.content

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetPickerTest {

    @Test
    fun theSessionStartsOnTheDaysSnippetAndCountsItAsSeen() {
        val shown = SnippetShown.startingAt(dayIndex = 4)

        assertEquals(4, shown.currentIndex)
        assertEquals(setOf(4), shown.seenIndices)
    }

    @Test
    fun aTapNeverShowsTheSnippetOnScreen() {
        repeat(200) { seed ->
            val shown = SnippetShown.startingAt(dayIndex = 2)
            val next = SnippetPicker.next(shown, snippetCount = 5, random = Random(seed))

            assertNotEquals(2, next.currentIndex)
        }
    }

    @Test
    fun noSnippetRepeatsUntilAllHaveBeenSeen() {
        val snippetCount = 12
        var shown = SnippetShown.startingAt(dayIndex = 7)
        val order = mutableListOf(shown.currentIndex)
        val random = Random(42)

        repeat(snippetCount - 1) {
            shown = SnippetPicker.next(shown, snippetCount, random)
            order.add(shown.currentIndex)
        }

        assertEquals(snippetCount, order.toSet().size)
        assertEquals(7, order.first())
    }

    @Test
    fun theNextRoundStartsAfterAllWereSeenAndSkipsTheCurrentOne() {
        val snippetCount = 4
        var shown = SnippetShown.startingAt(dayIndex = 0)
        val random = Random(1)
        repeat(snippetCount - 1) { shown = SnippetPicker.next(shown, snippetCount, random) }
        val lastOfFirstRound = shown.currentIndex

        val firstOfSecondRound = SnippetPicker.next(shown, snippetCount, random)

        assertNotEquals(lastOfFirstRound, firstOfSecondRound.currentIndex)
        assertEquals(
            setOf(lastOfFirstRound, firstOfSecondRound.currentIndex),
            firstOfSecondRound.seenIndices,
        )
    }

    @Test
    fun theDaysSnippetKeepsItsOwnPlaceInTheFirstRound() {
        val shown = SnippetShown.startingAt(dayIndex = 3)
        val next = SnippetPicker.next(shown, snippetCount = 6, random = Random(9))

        assertTrue(3 in next.seenIndices)
        assertEquals(2, next.seenIndices.size)
    }

    @Test
    fun aSingleSnippetStaysOnScreen() {
        val shown = SnippetShown.startingAt(dayIndex = 0)

        assertEquals(shown, SnippetPicker.next(shown, snippetCount = 1, random = Random(0)))
    }
}
