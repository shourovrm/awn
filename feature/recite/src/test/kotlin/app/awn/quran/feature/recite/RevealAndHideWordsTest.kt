package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The user found the hide-words switch and the two reveal buttons "buggy and stale" after several
 * presses (2026-10-22). The fixture holds 1:1 (indexes 0 and 1) and 1:2 (index 2) on page 1 and 1:3
 * on page 2.
 */
class RevealAndHideWordsTest {
    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private suspend fun hidingFrom(ayah: AyahRef): ControllerFixture {
        val fixture = ControllerFixture(folder.root)
        fixture.controller.reciteFrom(ayah)
        fixture.controller.joinPendingWork()
        fixture.controller.setHideWords(true)
        return fixture
    }

    @Test
    fun theSwitchChangesTheSessionAtOnceSoTwoQuickTapsEndOff() = runTest {
        val fixture = hidingFrom(AyahRef(1, 1))
        assertTrue(fixture.controller.session.hifzEnabled.value)

        fixture.controller.setHideWords(false)

        assertFalse(fixture.controller.session.hifzEnabled.value)
        assertEquals(WordState.Neutral, fixture.controller.session.wordStates.value[fixture.first])
    }

    @Test
    fun hidingAgainHidesTheWordsThatWereRevealedBefore() = runTest {
        val fixture = hidingFrom(AyahRef(1, 1))
        fixture.controller.session.revealNextWord(showsFirstLetterFirst = false)
        assertEquals(WordState.Neutral, fixture.controller.session.wordStates.value[fixture.first])

        fixture.controller.setHideWords(false)
        fixture.controller.setHideWords(true)

        val session = fixture.controller.session
        assertEquals(WordState.Hidden, session.wordStates.value[fixture.first])
        assertEquals(fixture.first, session.currentWord.value)
    }

    @Test
    fun showingTheWordsAgainDropsAPendingGradeAndHint() = runTest {
        val fixture = hidingFrom(AyahRef(1, 1))
        fixture.controller.session.revealAyah()
        assertNotNull(fixture.controller.session.gradePrompt.value)

        fixture.controller.setHideWords(false)

        assertNull(fixture.controller.session.gradePrompt.value)
        assertNull(fixture.controller.session.hint.value)
    }

    @Test
    fun revealingEveryLoadedWordLoadsTheNextPageSoTheButtonsKeepWorking() = runTest {
        val fixture = hidingFrom(AyahRef(1, 1))
        val session = fixture.controller.session

        session.revealAyah()
        assertEquals(3, session.words.value.size)
        session.revealAyah()

        assertEquals(
            listOf(fixture.first, fixture.second, fixture.third, fixture.fourth),
            session.words.value.map { it.key },
        )
        assertEquals(fixture.fourth, session.currentWord.value)
        session.revealNextWord(showsFirstLetterFirst = false)
        assertEquals(WordState.Neutral, session.wordStates.value[fixture.fourth])
    }
}
