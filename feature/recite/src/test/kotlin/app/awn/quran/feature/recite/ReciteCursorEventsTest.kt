package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What the session and the controller make of the cursor tracker's events: words passed over are
 * Unread and never a mistake, a word said right after being wrong is listed as corrected and not
 * counted, the current word moves back while the user repeats, and the page never turns by itself.
 */
class ReciteCursorEventsTest {

    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    @Test
    fun wordsPassedOverAreUnreadAndNotReviewed() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(
            TrackerEvent.WordChanged(0, WordState.Unread),
            TrackerEvent.WordChanged(1, WordState.Unread),
            TrackerEvent.JumpedAhead(fromWord = 0, toWord = 2),
            fixture.confirmed(2),
        )

        fixture.session.start()

        assertEquals(WordState.Unread, fixture.session.wordStates.value[fixture.first])
        assertTrue(fixture.session.reviews.value.isEmpty())
        assertTrue(fixture.session.wordProblems.value.isEmpty())
        assertNull(fixture.session.currentWord.value)
    }

    @Test
    fun anAyahPassedOverGetsNoHifzGrade() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.script(
            TrackerEvent.WordChanged(0, WordState.Unread),
            TrackerEvent.WordChanged(1, WordState.Unread),
            fixture.confirmed(2),
        )

        fixture.session.start()

        assertEquals(AyahRef(1, 2), fixture.session.gradePrompt.value?.ayah)
        assertEquals(WordState.Hidden, fixture.session.wordStates.value[fixture.first])
    }

    @Test
    fun aWordSaidRightAfterBeingLeftOutIsListedAsCorrectedAndNotCounted() = runTest {
        val fixture = SessionFixture(inputBlocks = 2)
        fixture.session.load(fixture.range)
        fixture.session.setHifzEnabled(true)
        fixture.script(
            TrackerEvent.WordChanged(0, WordState.Wrong),
            TrackerEvent.Skipped(0),
            fixture.confirmed(1),
        )
        fixture.script(
            TrackerEvent.Repeated(firstWord = 0, lastWord = 1),
            fixture.confirmed(0),
            TrackerEvent.Corrected(0),
        )

        fixture.session.start()

        val findings = fixture.session.reviews.value.getValue(AyahRef(1, 1)).single().findings
        assertEquals(listOf(ReviewFinding.Corrected), findings)
        assertEquals(WordProblem.Corrected, fixture.session.wordProblems.value[fixture.first])
        assertFalse(fixture.first in fixture.session.skippedWords.value)
        assertEquals(WordState.Confirmed, fixture.session.wordStates.value[fixture.first])
    }

    @Test
    fun theCurrentWordMovesBackWhileTheUserRepeats() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(
            fixture.confirmed(0),
            fixture.confirmed(1),
            fixture.confirmed(2),
            TrackerEvent.Repeated(firstWord = 0, lastWord = 2),
        )

        fixture.session.start()

        assertEquals(fixture.second, fixture.session.currentWord.value)
    }

    @Test
    fun recitingPastThePageOffersTheNextPageWithoutTurningIt() = runTest {
        val fixture = ControllerFixture(folder.root, inputBlocks = 3)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))
        fixture.script(fixture.confirmed(1))
        fixture.script(fixture.confirmed(2))

        fixture.controller.session.start()
        fixture.controller.joinPendingWork()

        // Ayah 1:3, where the user is now, is on page 2; the page shown is page 1.
        assertEquals(fixture.fourth, fixture.controller.session.currentWord.value)
        assertEquals(AyahRef(1, 3), fixture.controller.nextPageAyah.value)
        assertTrue(fixture.opened.isEmpty())

        fixture.controller.openNextPage()
        fixture.controller.joinPendingWork()

        assertEquals(listOf(AyahRef(1, 3)), fixture.opened)
        assertNull(fixture.controller.nextPageAyah.value)
    }

    @Test
    fun aWordOfTheAyahOnTheNextPageOffersTheNextPage() = runTest {
        val fixture = ControllerFixture(folder.root, inputBlocks = 1)
        // Ayah 1:1 runs over the page break: its second word opens page 2, where 1:2 starts.
        fixture.text.pageByAyah[AyahRef(1, 2)] = 2
        fixture.text.pages[2] =
            PageLayout(
                page = 2,
                script = ScriptId.V2,
                lines =
                    listOf(
                        PageLine(
                            line = 1,
                            items =
                                listOf(
                                    LineItem.WordItem(Word(fixture.second, "ٱللَّهِ", null, null)),
                                    LineItem.WordItem(Word(fixture.third, "ٱلْحَمْدُ", null, null)),
                                ),
                        ),
                    ),
            )
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(fixture.confirmed(0))

        fixture.controller.session.start()
        fixture.controller.joinPendingWork()

        assertEquals(fixture.second, fixture.controller.session.currentWord.value)
        assertEquals(AyahRef(1, 2), fixture.awaitNextPageAyah { it != null })

        fixture.controller.openNextPage()
        fixture.controller.joinPendingWork()

        assertNull(fixture.awaitNextPageAyah { it == null })
    }

    // The page check runs where the settings store resumed the load, possibly another thread.
    private suspend fun ControllerFixture.awaitNextPageAyah(
        isReady: (AyahRef?) -> Boolean,
    ): AyahRef? =
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MILLIS) { controller.nextPageAyah.first(isReady) }
        }

    @Test
    fun aWordWaitingForItsLastSoundIsClosedWhenTheRunEnds() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(fixture.confirmed(0))
        fixture.tracker.pendingBatch += fixture.confirmed(1)

        fixture.session.start()

        assertEquals(1, fixture.tracker.closePendingCount)
        assertEquals(WordState.Confirmed, fixture.session.wordStates.value[fixture.second])
        assertEquals(fixture.third, fixture.session.currentWord.value)
    }

    @Test
    fun aRunPausedForListeningGoesOnAfterTheClosedWord() = runTest {
        val fixture = SessionFixture()
        fixture.session.load(fixture.range)
        fixture.script(fixture.confirmed(0))
        fixture.tracker.pendingBatch += fixture.confirmed(1)
        fixture.session.start()

        fixture.session.pauseForListening()
        fixture.session.start()

        assertEquals(listOf(2), fixture.tracker.continuedFrom)
        assertEquals(WordState.Confirmed, fixture.session.wordStates.value[fixture.second])
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
