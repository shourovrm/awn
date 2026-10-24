package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import app.awn.quran.tracker.WordState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A bounded run loads the ayahs it is given and not the rest of their page. */
class PracticeAyahsTest {

    @get:Rule val folder = TemporaryFolder()

    @get:Rule val fixtures = ControllerFixtures()

    private suspend fun ControllerFixture.awaitSessionHiding(expected: Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { controller.session.hifzEnabled.first { it == expected } }
        }
    }

    @Test
    fun aRunOverOneAyahLoadsOnlyThatAyah() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.practiceAyahs(listOf(AyahRef(1, 1)), hideWords = false)
        fixture.controller.joinPendingWork()

        // Ayah 2 is on the same page; reciteFrom would have loaded it too.
        assertEquals(
            listOf(fixture.first, fixture.second),
            fixture.controller.session.words.value.map { it.key },
        )
        assertEquals(true, fixture.controller.isActive.value)
    }

    @Test
    fun aRunOverTwoAyahsLoadsBothAndNothingAfterThem() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.practiceAyahs(listOf(AyahRef(1, 1), AyahRef(1, 2)), hideWords = false)
        fixture.controller.joinPendingWork()

        assertEquals(
            listOf(fixture.first, fixture.second, fixture.third),
            fixture.controller.session.words.value.map { it.key },
        )
    }

    @Test
    fun aRunThatHidesTheWordsHidesThem() = runTest {
        val fixture = ControllerFixture(folder.root)

        fixture.controller.practiceAyahs(listOf(AyahRef(1, 1)), hideWords = true)
        fixture.controller.joinPendingWork()
        fixture.awaitSessionHiding(true)

        assertEquals(WordState.Hidden, fixture.controller.session.wordStates.value[fixture.first])
    }

    @Test(expected = IllegalArgumentException::class)
    fun aRunWithNoAyahsIsRefused() {
        ControllerFixture(folder.root).controller.practiceAyahs(emptyList(), hideWords = false)
    }
}
