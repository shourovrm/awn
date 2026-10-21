package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.tracker.TrackerEvent
import app.awn.quran.tracker.WordProblem
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The mistake the user is looking at: which word it is, and what Listen plays. */
class ReciteFocusTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private suspend fun reciteWithFirstWordSkipped(fixture: ControllerFixture) {
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()
        fixture.script(TrackerEvent.Skipped(0), TrackerEvent.WordChanged(2, WordState.Wrong))
        fixture.controller.session.start()
    }

    @Test
    fun noWordIsFocusedAtFirst() {
        val fixture = ControllerFixture(folder.root)

        assertNull(fixture.controller.focusedWord.value)
    }

    @Test
    fun focusingAFlaggedWordKeepsItUntilItIsCleared() = runTest {
        val fixture = ControllerFixture(folder.root)
        reciteWithFirstWordSkipped(fixture)

        fixture.controller.focusWord(fixture.third)
        assertEquals(fixture.third, fixture.controller.focusedWord.value)

        fixture.controller.clearFocus()
        assertNull(fixture.controller.focusedWord.value)
    }

    @Test
    fun leavingReciteModeClearsTheFocus() = runTest {
        val fixture = ControllerFixture(folder.root)
        reciteWithFirstWordSkipped(fixture)
        fixture.controller.focusWord(fixture.first)

        fixture.controller.exit()
        fixture.controller.joinPendingWork()

        assertNull(fixture.controller.focusedWord.value)
    }

    @Test
    fun startingAgainDropsAFocusOnAWordThatIsNoLongerFlagged() = runTest {
        val fixture = ControllerFixture(folder.root)
        reciteWithFirstWordSkipped(fixture)
        fixture.controller.focusWord(fixture.first)

        fixture.controller.session.reset()

        assertNull(fixture.controller.focusedWord.value)
    }

    @Test
    fun listenPlaysTheAyahOfTheFocusedWordNotOfTheNextExpectedOne() = runTest {
        val fixture = ControllerFixture(folder.root)
        reciteWithFirstWordSkipped(fixture)
        // The next expected word is still in ayah 1; the user is looking at a mistake in ayah 2.
        fixture.controller.focusWord(fixture.third)

        assertEquals(AyahRef(1, 2), fixture.controller.ayahToListenTo())
    }

    @Test
    fun listenPlaysTheFocusedWordWhenTheReciterHasWordTimings() = runTest {
        val fixture = ControllerFixture(folder.root, hasWordTimings = true)
        reciteWithFirstWordSkipped(fixture)
        fixture.controller.focusWord(fixture.third)

        fixture.controller.listenToAyah()

        val reciter = fixture.settings.reciterId.first()
        assertEquals(listOf(Triple(reciter, AyahRef(1, 2), 1)), fixture.audioPlayer.wordCalls)
        assertEquals(emptyList<Any>(), fixture.audioPlayer.playCalls)
    }

    @Test
    fun listenPlaysTheAyahOfTheFocusedWordWithoutWordTimings() = runTest {
        val fixture = ControllerFixture(folder.root, hasWordTimings = false)
        reciteWithFirstWordSkipped(fixture)
        fixture.controller.focusWord(fixture.third)

        fixture.controller.listenToAyah()

        val reciter = fixture.settings.reciterId.first()
        assertEquals(
            listOf(Triple(reciter, AyahRef(1, 2), AyahRef(1, 2))),
            fixture.audioPlayer.playCalls,
        )
        assertEquals(emptyList<Any>(), fixture.audioPlayer.wordCalls)
    }

    @Test
    fun withoutAFocusListenKeepsPlayingTheNextExpectedAyah() = runTest {
        val fixture = ControllerFixture(folder.root, hasWordTimings = true)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.listenToAyah()

        val reciter = fixture.settings.reciterId.first()
        assertEquals(
            listOf(Triple(reciter, AyahRef(1, 1), AyahRef(1, 1))),
            fixture.audioPlayer.playCalls,
        )
    }

    @Test
    fun anUnclearWordWithoutFindingsStillGetsAReviewEntry() {
        val reviewed = WordReview(WordKey(1, 1, 2), listOf(ReviewFinding.WrongWord))
        val reviews = mapOf(AyahRef(1, 1) to listOf(reviewed))
        val problems =
            mapOf(
                WordKey(1, 2, 1) to WordProblem.NotClear,
                WordKey(1, 1, 2) to WordProblem.DifferentWord,
            )

        assertEquals(
            listOf(reviewed, WordReview(WordKey(1, 2, 1), listOf(ReviewFinding.NotClear))),
            flaggedReviews(reviews, problems),
        )
    }
}
