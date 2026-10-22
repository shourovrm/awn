package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Three reported problems with Hifz practice and revision runs:
 * - Practice always started with the words hidden; a new lesson must first show the ayah, and the
 *   user hides the words when they are ready.
 * - Practice and revision saved "hide words" as the user's recite setting, so plain reciting stayed
 *   hidden afterwards. Hiding during a run now lasts for that run only.
 * - "Revise 10 ayahs" on the Today row revised every due ayah: the run takes a limit.
 */
class ReciteRunScopeTest {

    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    // The session follows changes on another thread, so wait for the state rather than sleep.
    private suspend fun ControllerFixture.awaitSessionHiding(expected: Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { controller.session.hifzEnabled.first { it == expected } }
        }
    }

    private suspend fun ControllerFixture.savedHideSetting(): Boolean =
        withContext(Dispatchers.Default) { settings.hifzEnabled.first() }

    @Test
    fun practiceOfANewLessonStartsWithTheWordsShown() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.practiceFrom(AyahRef(1, 1), hideWords = false)
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(false)

        assertTrue(fixture.controller.isActive.value)
        assertNotEquals(
            WordState.Hidden,
            fixture.controller.session.wordStates.value[fixture.first],
        )
    }

    @Test
    fun practiceThatAsksForHiddenWordsHidesThem() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.practiceFrom(AyahRef(1, 1), hideWords = true)
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(true)

        assertEquals(WordState.Hidden, fixture.controller.session.wordStates.value[fixture.first])
    }

    @Test
    fun hidingTheWordsDuringPracticeDoesNotChangeTheSavedSetting() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.practiceFrom(AyahRef(1, 1), hideWords = false)
        fixture.controller.joinPendingWork()

        fixture.controller.setHideWords(true)
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(true)

        assertFalse(fixture.savedHideSetting())
    }

    @Test
    fun afterPracticeEndsPlainRecitingFollowsTheSavedSettingAgain() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.practiceFrom(AyahRef(1, 1), hideWords = true)
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(true)

        fixture.controller.exit()
        fixture.controller.joinPendingWork()
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.awaitSessionHiding(false)
        assertFalse(fixture.savedHideSetting())
    }

    @Test
    fun revisionHidesTheWordsWithoutChangingTheSavedSetting() = runTest {
        val fixture = ControllerFixture(folder.root, dueAyahs = mutableListOf(AyahRef(1, 3)))

        fixture.controller.reviseAt(AyahRef(1, 3))
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(true)

        assertFalse(fixture.savedHideSetting())
    }

    @Test
    fun outsideARunTheHideButtonStillSavesTheChoice() = runTest {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        fixture.controller.setHideWords(true)
        fixture.controller.joinPendingWork()

        withContext(Dispatchers.Default) {
            withTimeout(5_000) { fixture.settings.hifzEnabled.first { it } }
        }
    }

    @Test
    fun aRevisionLimitCapsTheAyahsInTheRun() = runTest {
        val due = (1..3).map { AyahRef(1, it) } + AyahRef(2, 1)
        val fixture = ControllerFixture(folder.root, dueAyahs = due.toMutableList())

        fixture.controller.reviseAt(AyahRef(1, 1), limit = 2)
        fixture.controller.joinPendingWork()

        assertEquals(2, fixture.controller.revisionRemaining.value)
    }

    @Test
    fun theRunEndsWhenTheLimitIsReachedEvenWithMoreAyahsDue() = runTest {
        val due = listOf(AyahRef(1, 1), AyahRef(1, 3), AyahRef(2, 1))
        val fixture = ControllerFixture(folder.root, dueAyahs = due.toMutableList())
        fixture.controller.reviseAt(AyahRef(1, 1), limit = 1)
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(true)
        fixture.controller.session.revealAyah()

        fixture.controller.grade(AyahGrade.Good)
        fixture.controller.joinPendingWork()

        assertFalse(fixture.controller.isRevising.value)
        assertTrue(fixture.opened.isEmpty())
    }

    @Test
    fun withoutALimitTheRunCoversEveryDueAyah() = runTest {
        val due = listOf(AyahRef(1, 1), AyahRef(1, 3), AyahRef(2, 1))
        val fixture = ControllerFixture(folder.root, dueAyahs = due.toMutableList())

        fixture.controller.reviseAt(AyahRef(1, 1))
        fixture.controller.joinPendingWork()

        assertEquals(3, fixture.controller.revisionRemaining.value)
    }
}
