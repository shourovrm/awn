package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.PlayButtonMode
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.player.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The word card and the ayah bar: when they close, and that they never show together. */
class QuranPopupsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val viewModel by lazy { QuranViewModel(fixture.dependencies, wordLanguage = "en") }
    private val firstAyah = AyahRef(1, 1)

    private fun aWord(): AyahWord =
        AyahWord(
            key = ReadFixture.word(1, 1, 1, ReadFixture.FIRST_WORD).key,
            arabic = ReadFixture.FIRST_WORD,
            meaning = "In (the) name",
            transliteration = "bismi",
        )

    @Test
    fun aTapOutsideClosesTheWordCardAndLeavesTheBarsAlone() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val barsBefore = viewModel.state.value.areBarsVisible
        viewModel.onWordTapped(firstAyah, aWord())
        assertNotNull(viewModel.state.value.wordPopup)

        viewModel.onPageTapped()

        assertNull(viewModel.state.value.wordPopup)
        assertEquals(barsBefore, viewModel.state.value.areBarsVisible)
    }

    @Test
    fun aTapOutsideClosesTheAyahBarAndLeavesTheBarsAlone() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val barsBefore = viewModel.state.value.areBarsVisible
        viewModel.selectAyah(firstAyah)

        viewModel.onPageTapped()

        assertNull(viewModel.state.value.selection)
        assertEquals(barsBefore, viewModel.state.value.areBarsVisible)
    }

    @Test
    fun aTapWithNothingOpenShowsOrHidesTheBars() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val barsBefore = viewModel.state.value.areBarsVisible

        viewModel.onPageTapped()

        assertEquals(!barsBefore, viewModel.state.value.areBarsVisible)
    }

    @Test
    fun aTapAboveTheOpenPanelStillTogglesTheBars() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.selectAyah(firstAyah)
        viewModel.openPanel()
        val barsBefore = viewModel.state.value.areBarsVisible

        viewModel.onPageTapped()

        assertTrue(viewModel.state.value.isPanelOpen)
        assertEquals(!barsBefore, viewModel.state.value.areBarsVisible)
    }

    @Test
    fun tappingAWordClosesTheAyahBar() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.selectAyah(firstAyah)

        viewModel.onWordTapped(firstAyah, aWord())

        assertNull(viewModel.state.value.selection)
        assertNotNull(viewModel.state.value.wordPopup)
    }

    @Test
    fun selectingAnAyahClosesTheWordCard() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, aWord())

        viewModel.selectRange(AyahRange(firstAyah))

        assertNull(viewModel.state.value.wordPopup)
        assertEquals(firstAyah, viewModel.state.value.selection)
    }

    @Test
    fun backStillClosesTheWordCardBeforeThePanel() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.onWordTapped(firstAyah, aWord())

        assertTrue(viewModel.onBack())

        assertNull(viewModel.state.value.wordPopup)
    }

    @Test
    fun playStartsTheSelectionAndClosesTheBar() = runTest {
        viewModel.open(QuranTarget.Page(1))
        viewModel.selectAyah(firstAyah)
        var started = 0

        viewModel.onPlayPressed { started++ }

        assertEquals(1, started)
        assertNull(viewModel.state.value.selection)
    }

    @Test
    fun theButtonPausesWhileTheSelectedAyahPlaysAndResumesWhenPaused() = runTest {
        viewModel.open(QuranTarget.Page(1))
        fixture.player.currentState.value = PlaybackState.Playing("r", firstAyah, null, 0L, null)
        viewModel.selectAyah(firstAyah)
        assertEquals(PlayButtonMode.Pause, viewModel.state.value.playButtonMode)
        var started = 0

        viewModel.onPlayPressed { started++ }

        assertEquals(0, started)
        assertTrue(fixture.player.currentState.value is PlaybackState.Paused)
        // Pausing leaves the bar open so the next press can resume.
        assertEquals(firstAyah, viewModel.state.value.selection)
        assertEquals(PlayButtonMode.Resume, viewModel.state.value.playButtonMode)

        viewModel.onPlayPressed { started++ }

        assertEquals(0, started)
        assertTrue(fixture.player.currentState.value is PlaybackState.Playing)
        assertNull(viewModel.state.value.selection)
    }

    @Test
    fun anotherAyahPlayingLeavesTheButtonOnPlay() = runTest {
        viewModel.open(QuranTarget.Page(1))
        fixture.player.currentState.value =
            PlaybackState.Playing("r", AyahRef(1, 5), null, 0L, null)

        viewModel.selectAyah(firstAyah)

        assertEquals(PlayButtonMode.Play, viewModel.state.value.playButtonMode)
        assertFalse(viewModel.state.value.isAudioIdle)
    }

    @Test
    fun aDraggedRangeThatHoldsThePlayingAyahOffersPause() = runTest {
        viewModel.open(QuranTarget.Page(1))
        fixture.player.currentState.value =
            PlaybackState.Playing("r", AyahRef(1, 2), null, 0L, null)

        viewModel.selectRange(AyahRange(AyahRef(1, 1), AyahRef(1, 3)))

        assertEquals(PlayButtonMode.Pause, viewModel.state.value.playButtonMode)
    }
}
