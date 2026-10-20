package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.quran.Popup
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuranViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val viewModel by lazy { QuranViewModel(fixture.dependencies, wordLanguage = "en") }

    @Test
    fun backClosesPopupsThenPanelThenSelectionThenReciteThenBarsThenLeaves() = runTest {
        viewModel.open(null)
        viewModel.selectAyah(AyahRef(1, 1))
        viewModel.openPanel()
        viewModel.openPopup(Popup.Overflow)
        viewModel.enterReciteMode() // clears selection and panel, so reselect below
        viewModel.selectAyah(AyahRef(1, 1))
        viewModel.openPanel()
        viewModel.openPopup(Popup.TextSize)

        assertTrue(viewModel.onBack())
        assertEquals(Popup.None, viewModel.state.value.popup)
        assertTrue(viewModel.state.value.isPanelOpen)

        assertTrue(viewModel.onBack())
        assertFalse(viewModel.state.value.isPanelOpen)
        assertEquals(AyahRef(1, 1), viewModel.state.value.selection)

        assertTrue(viewModel.onBack())
        assertEquals(null, viewModel.state.value.selection)
        assertTrue(viewModel.state.value.isReciting)

        assertTrue(viewModel.onBack())
        assertFalse(viewModel.state.value.isReciting)
        assertTrue(viewModel.state.value.areBarsVisible)

        assertTrue(viewModel.onBack())
        assertFalse(viewModel.state.value.areBarsVisible)
        assertFalse(viewModel.state.value.hasBackWork)

        assertFalse(viewModel.onBack())
    }

    @Test
    fun backNeverChangesTheSavedView() = runTest {
        fixture.store.update { it.copy(pageView = PageView.WordByWord) }
        viewModel.open(null)
        viewModel.selectAyah(AyahRef(1, 1))
        viewModel.openPanel()

        while (viewModel.onBack()) Unit

        assertEquals(PageView.WordByWord, fixture.store.settings.first().pageView)
    }

    @Test
    fun viewButtonCyclesThroughThreeViewsAndSavesThem() = runTest {
        viewModel.open(null)

        viewModel.cycleView()
        assertEquals(PageView.Translation, fixture.store.settings.first().pageView)
        viewModel.cycleView()
        assertEquals(PageView.WordByWord, fixture.store.settings.first().pageView)
        viewModel.cycleView()
        assertEquals(PageView.Mushaf, fixture.store.settings.first().pageView)
    }

    @Test
    fun openingAtAnAyahSelectsItAndHidesTheBars() = runTest {
        viewModel.open(QuranTarget.Ayah(AyahRef(2, 1)))

        val state = viewModel.state.value
        assertEquals(2, state.currentPage)
        assertEquals(AyahRef(2, 1), state.selection)
        assertFalse(state.areBarsVisible)
    }

    @Test
    fun jumpingMovesThePagerInEveryView() = runTest {
        for (view in PageView.entries) {
            fixture.store.update { it.copy(pageView = view) }
            viewModel.open(QuranTarget.Page(1))
            val tokenBefore = viewModel.state.value.jumpToken

            viewModel.jumpToPage(2)

            val state = viewModel.state.value
            assertEquals(2, state.currentPage)
            assertTrue(state.jumpToken > tokenBefore)
            viewModel.jumpToPage(1)
        }
    }

    @Test
    fun aPageIsReadOnlyAfterTwentySecondsOnScreen() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(19_999)
        assertTrue(fixture.progress.openedPages.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf(1), fixture.progress.openedPages)
    }

    @Test
    fun leavingAPageBeforeTwentySecondsDoesNotCountIt() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(10_000)
        viewModel.onPageSettled(2)
        advanceTimeBy(10_000)

        assertTrue(fixture.progress.openedPages.isEmpty())
        // The recent-pages list, used to resume, is still updated at once.
        assertEquals(2, fixture.bookmarks.recentPages().first().first().page)

        advanceTimeBy(10_001)
        assertEquals(listOf(2), fixture.progress.openedPages)
    }

    @Test
    fun theTimerPausesWhileTheScreenIsNotInFront() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(15_000)
        viewModel.setScreenActive(false)
        advanceTimeBy(60_000)
        assertTrue(fixture.progress.openedPages.isEmpty())

        viewModel.setScreenActive(true)
        advanceTimeBy(20_001)
        assertEquals(listOf(1), fixture.progress.openedPages)
    }

    @Test
    fun theSamePageIsReportedOnce() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(21_000)
        viewModel.setScreenActive(false)
        viewModel.setScreenActive(true)
        advanceTimeBy(21_000)

        assertEquals(listOf(1), fixture.progress.openedPages)
    }

    @Test
    fun playbackOnTheNextPageTurnsThePage() = runTest {
        viewModel.open(QuranTarget.Page(1))
        fixture.player.currentState.value =
            PlaybackState.Playing("r", AyahRef(1, 7), null, 0L, null)
        assertEquals(1, viewModel.state.value.currentPage)
        assertEquals(AyahRef(1, 7), viewModel.state.value.playingAyah)

        fixture.player.currentState.value = PlaybackState.Playing("r", AyahRef(2, 1), 2, 0L, null)

        val state = viewModel.state.value
        assertEquals(2, state.currentPage)
        assertEquals(2, state.playingWordPosition)
    }

    @Test
    fun playbackDoesNotPullBackAReaderWhoSwipedAway() = runTest {
        viewModel.open(QuranTarget.Page(1))
        fixture.player.currentState.value =
            PlaybackState.Playing("r", AyahRef(1, 7), null, 0L, null)
        viewModel.onPageSettled(2)
        viewModel.onPageSettled(1)
        viewModel.onPageSettled(2)
        fixture.player.currentState.value =
            PlaybackState.Playing("r", AyahRef(1, 6), null, 0L, null)

        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun panelArrowsMoveTheSelectionAndThePageFollows() = runTest {
        viewModel.open(QuranTarget.Ayah(AyahRef(1, 7)))
        viewModel.openPanel()

        viewModel.selectAdjacentAyah(1)

        val state = viewModel.state.value
        assertEquals(AyahRef(2, 1), state.selection)
        assertEquals(2, state.currentPage)
        assertTrue(state.isPanelOpen)
    }
}
