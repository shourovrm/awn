package app.awn.quran.feature.read

import app.awn.quran.feature.read.quran.ReciteFollowDecision
import app.awn.quran.feature.read.quran.decideReciteFollow
import app.awn.quran.feature.read.quran.isSinglePageTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciteFollowPolicyTest {
    @Test
    fun aWordOnTheShownPageKeepsThePage() {
        val decision = decideReciteFollow(wordPage = 5, shownPage = 5, isPaused = false)

        assertEquals(ReciteFollowDecision(turnToPage = null, isPaused = false), decision)
    }

    @Test
    fun aWordOnTheNextPageTurnsToIt() {
        val decision = decideReciteFollow(wordPage = 6, shownPage = 5, isPaused = false)

        assertEquals(ReciteFollowDecision(turnToPage = 6, isPaused = false), decision)
    }

    @Test
    fun aReaderWhoSwipedAwayIsNotPulledBack() {
        val decision = decideReciteFollow(wordPage = 6, shownPage = 5, isPaused = true)

        assertEquals(ReciteFollowDecision(turnToPage = null, isPaused = true), decision)
    }

    @Test
    fun reciteAWordOnTheShownPageEndsThePause() {
        val decision = decideReciteFollow(wordPage = 5, shownPage = 5, isPaused = true)

        assertFalse(decision.isPaused)
    }

    @Test
    fun afterThePauseEndsTheNextPageTurnFollows() {
        val afterPause = decideReciteFollow(wordPage = 5, shownPage = 5, isPaused = true)

        val next = decideReciteFollow(wordPage = 6, shownPage = 5, isPaused = afterPause.isPaused)

        assertEquals(6, next.turnToPage)
    }

    @Test
    fun reciteStartedOnAnotherPageTurnsToIt() {
        val decision = decideReciteFollow(wordPage = 300, shownPage = 5, isPaused = false)

        assertEquals(300, decision.turnToPage)
    }

    @Test
    fun onlyTheNeighbouringPagesAreAnAnimatedTurn() {
        assertTrue(isSinglePageTurn(shownPage = 5, targetPage = 6))
        assertTrue(isSinglePageTurn(shownPage = 5, targetPage = 4))
        assertFalse(isSinglePageTurn(shownPage = 5, targetPage = 7))
        assertFalse(isSinglePageTurn(shownPage = 5, targetPage = 5))
    }
}
