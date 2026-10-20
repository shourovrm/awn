package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.quran.BARS_REVEAL_MS
import app.awn.quran.feature.read.quran.Popup
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.quran.RESUME_DEBOUNCE_MS
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
        // The bars show for two seconds to teach that they exist, then hide.
        assertTrue(state.areBarsVisible)
        advanceTimeBy(BARS_REVEAL_MS + 1)
        assertFalse(viewModel.state.value.areBarsVisible)
    }

    @Test
    fun theBarsShowForTwoSecondsWhenTheScreenOpensThenHide() = runTest {
        viewModel.open(null)
        assertTrue(viewModel.state.value.areBarsVisible)

        advanceTimeBy(BARS_REVEAL_MS - 1)
        assertTrue(viewModel.state.value.areBarsVisible)

        advanceTimeBy(2)
        assertFalse(viewModel.state.value.areBarsVisible)
    }

    @Test
    fun barsTheReaderShowedByHandNeverHideThemselves() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(BARS_REVEAL_MS + 1)
        assertFalse(viewModel.state.value.areBarsVisible)

        viewModel.toggleBars()
        advanceTimeBy(60_000)

        assertTrue(viewModel.state.value.areBarsVisible)
    }

    @Test
    fun aTapDuringTheRevealKeepsTheBarsHidden() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(500)

        viewModel.toggleBars() // hides them
        advanceTimeBy(10_000)

        assertFalse(viewModel.state.value.areBarsVisible)
    }

    @Test
    fun backClosesThePanelAtOnceWithoutWaiting() = runTest {
        viewModel.open(QuranTarget.Ayah(AyahRef(1, 1)))
        viewModel.openPanel()

        viewModel.onBack()

        // No virtual time has passed: the state changed in the same call.
        assertFalse(viewModel.state.value.isPanelOpen)
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
        // The resume point follows a swipe once the page has settled for the debounce.
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
        assertEquals(2, viewModel.playingWordPosition.value)
    }

    private fun play(ayah: AyahRef) {
        fixture.player.currentState.value = PlaybackState.Playing("r", ayah, null, 0L, null)
    }

    @Test
    fun playbackDoesNotPullBackAReaderWhoDraggedAway() = runTest {
        viewModel.open(QuranTarget.Page(1))
        play(AyahRef(1, 7))
        viewModel.onPagerDragged()
        viewModel.onPageSettled(2)

        play(AyahRef(1, 6))

        assertEquals(2, viewModel.state.value.currentPage)
        assertFalse(viewModel.state.value.isFollowing)
        assertTrue(viewModel.state.value.showBackToPlaying)
    }

    @Test
    fun backToPlayingJumpsToTheAyahAndFollowsAgain() = runTest {
        viewModel.open(QuranTarget.Page(1))
        play(AyahRef(1, 7))
        viewModel.onPagerDragged()
        viewModel.onPageSettled(2)
        val tokenBefore = viewModel.state.value.jumpToken

        viewModel.backToPlaying()

        val state = viewModel.state.value
        assertEquals(1, state.currentPage)
        assertTrue(state.isFollowing)
        assertFalse(state.showBackToPlaying)
        assertTrue(state.jumpToken > tokenBefore)
    }

    @Test
    fun swipingBackToThePlayingPageFollowsAgain() = runTest {
        viewModel.open(QuranTarget.Page(1))
        play(AyahRef(1, 7))
        viewModel.onPagerDragged()
        viewModel.onPageSettled(2)

        viewModel.onPageSettled(1)

        assertTrue(viewModel.state.value.isFollowing)
    }

    @Test
    fun aDragThatReturnsToThePlayingPageFollowsAgain() = runTest {
        viewModel.open(QuranTarget.Page(1))
        play(AyahRef(1, 7))
        viewModel.onPagerDragged()

        viewModel.onPageSettled(1)

        assertTrue(viewModel.state.value.isFollowing)
    }

    @Test
    fun goingToAnotherPageWhileAudioPlaysStopsFollowing() = runTest {
        viewModel.open(QuranTarget.Page(1))
        play(AyahRef(1, 7))

        viewModel.jumpToPage(2)

        assertFalse(viewModel.state.value.isFollowing)
    }

    @Test
    fun aNewPlaybackIsFollowedEvenAfterTheReaderDraggedAway() = runTest {
        viewModel.open(QuranTarget.Page(1))
        play(AyahRef(1, 7))
        viewModel.onPagerDragged()
        viewModel.onPageSettled(2)
        fixture.player.currentState.value = PlaybackState.Idle

        play(AyahRef(1, 3))

        val state = viewModel.state.value
        assertTrue(state.isFollowing)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun theWordTickDoesNotChangeThePageState() = runTest {
        viewModel.open(QuranTarget.Page(1))
        fixture.player.currentState.value = PlaybackState.Playing("r", AyahRef(1, 7), 1, 0L, null)
        val before = viewModel.state.value

        fixture.player.currentState.value = PlaybackState.Playing("r", AyahRef(1, 7), 2, 0L, null)

        assertEquals(before, viewModel.state.value)
        assertEquals(2, viewModel.playingWordPosition.value)
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

    private suspend fun resumePage(): Int? =
        fixture.bookmarks.recentPages().first().firstOrNull()?.page

    @Test
    fun openingATargetDoesNotMoveTheResumePointUntilTheReaderStays() = runTest {
        fixture.bookmarks.recordPageOpened(1)

        viewModel.open(QuranTarget.Page(2))
        advanceTimeBy(RESUME_DEBOUNCE_MS - 1)
        assertEquals(1, resumePage())

        advanceTimeBy(2)
        assertEquals(2, resumePage())
    }

    @Test
    fun leavingAfterALookupKeepsTheOldResumePoint() = runTest {
        fixture.bookmarks.recordPageOpened(1)

        viewModel.setScreenActive(true)
        viewModel.open(QuranTarget.Ayah(AyahRef(2, 1)))
        advanceTimeBy(300)
        viewModel.setScreenActive(false)
        advanceTimeBy(30_000)

        assertEquals(1, resumePage())
    }

    @Test
    fun goToPageIsALookupToo() = runTest {
        fixture.bookmarks.recordPageOpened(1)
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(RESUME_DEBOUNCE_MS + 1)

        viewModel.jumpToPage(2)
        advanceTimeBy(RESUME_DEBOUNCE_MS - 1)
        assertEquals(1, resumePage())
    }

    @Test
    fun aSwipeIsSavedAtOnceWhenTheScreenStops() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(RESUME_DEBOUNCE_MS + 1)

        viewModel.onPageSettled(2)
        advanceTimeBy(100)
        assertEquals(1, resumePage())
        viewModel.setScreenActive(false)

        assertEquals(2, resumePage())
    }

    @Test
    fun theResumeSaveIsIndependentOfTheTwentySecondReadRule() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(RESUME_DEBOUNCE_MS + 1)

        assertEquals(1, resumePage())
        assertTrue(fixture.progress.openedPages.isEmpty())
    }

    @Test
    fun aDraggedRangeSelectsFromTheFirstToTheLastAyah() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.selectRange(AyahRange(AyahRef(1, 2), AyahRef(1, 5)))

        val state = viewModel.state.value
        assertEquals(AyahRef(1, 2), state.selection)
        assertEquals(AyahRef(1, 5), state.selectionEnd)
        assertEquals(AyahRange(AyahRef(1, 2), AyahRef(1, 5)), state.selectedRange)
    }

    @Test
    fun aRangeOfOneAyahIsASingleSelection() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.selectRange(AyahRange(AyahRef(1, 2), AyahRef(1, 2)))

        assertEquals(null, viewModel.state.value.selectionEnd)
        assertTrue(viewModel.state.value.selectedRange!!.isSingle)
    }

    @Test
    fun selectingOneAyahAfterARangeDropsTheRange() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.selectRange(AyahRange(AyahRef(1, 2), AyahRef(1, 5)))

        viewModel.selectAyah(AyahRef(1, 3))

        assertEquals(null, viewModel.state.value.selectionEnd)
    }

    @Test
    fun backClearsARangeInOneStep() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.selectRange(AyahRange(AyahRef(1, 2), AyahRef(1, 5)))
        viewModel.onBack() // bars (opened from resume)
        viewModel.onBack()

        assertEquals(null, viewModel.state.value.selection)
        assertEquals(null, viewModel.state.value.selectionEnd)
    }

    @Test
    fun theShareTextOfARangeHasOneLinePerAyah() = runTest {
        viewModel.open(QuranTarget.Page(1))

        val text = viewModel.shareTextOf(AyahRange(AyahRef(1, 1), AyahRef(1, 2)))

        assertEquals(2, text.lines().size)
    }

    @Test
    fun theVolumeKeysTurnPagesOnlyWhileAudioIsIdle() = runTest {
        viewModel.open(QuranTarget.Page(1))
        assertTrue(viewModel.state.value.isAudioIdle)

        play(AyahRef(1, 1))
        assertFalse(viewModel.state.value.isAudioIdle)

        fixture.player.currentState.value = PlaybackState.Paused("r", AyahRef(1, 1), 0L)
        assertFalse(viewModel.state.value.isAudioIdle)

        fixture.player.currentState.value = PlaybackState.Downloading(0L, null)
        assertFalse(viewModel.state.value.isAudioIdle)

        fixture.player.currentState.value = PlaybackState.Error("x")
        assertTrue(viewModel.state.value.isAudioIdle)

        fixture.player.currentState.value = PlaybackState.Idle
        assertTrue(viewModel.state.value.isAudioIdle)
    }

    @Test
    fun turningAPageWithTheKeysIsReadingSoItIsSavedWhenTheScreenStops() = runTest {
        viewModel.open(QuranTarget.Page(1))
        advanceTimeBy(RESUME_DEBOUNCE_MS + 1)

        viewModel.turnPage(1)
        viewModel.setScreenActive(false)

        assertEquals(2, resumePage())
        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun everyEntryOpensInTheSavedViewAndLeavesItAlone() = runTest {
        val targets = listOf(null, QuranTarget.Page(2), QuranTarget.Ayah(AyahRef(2, 1)))
        for (view in PageView.entries) {
            for (target in targets) {
                val entryFixture = ReadFixture()
                entryFixture.store.update { it.copy(pageView = view) }
                val entryModel = QuranViewModel(entryFixture.dependencies, wordLanguage = "en")

                entryModel.open(target)

                assertEquals("$view via $target", view, entryModel.state.value.settings.pageView)
                assertEquals(view, entryFixture.store.settings.first().pageView)
            }
        }
    }

    @Test
    fun theScreenWaitsForTheSavedViewBeforeItShowsAPage() = runTest {
        val late =
            kotlinx.coroutines.CompletableDeferred<
                app.awn.quran.feature.read.settings.ReadSettings,
            >()
        val lateStore =
            object : app.awn.quran.feature.read.settings.ReadSettingsStore {
                override val settings = kotlinx.coroutines.flow.flow { emit(late.await()) }

                override suspend fun update(
                    transform:
                        (
                            app.awn.quran.feature.read.settings.ReadSettings,
                        ) -> app.awn.quran.feature.read.settings.ReadSettings,
                ) = Unit
            }
        val dependencies = fixture.dependencies.copy(settingsStore = lateStore)
        val model = QuranViewModel(dependencies, wordLanguage = "en")

        model.open(QuranTarget.Page(1))
        assertFalse(model.state.value.hasStarted)

        late.complete(
            app.awn.quran.feature.read.settings.ReadSettings(pageView = PageView.Translation),
        )

        assertTrue(model.state.value.hasStarted)
        assertEquals(PageView.Translation, model.state.value.settings.pageView)
    }
}
