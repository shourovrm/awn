package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The page follows the next word the reciter is expected to say. */
class ReciteFollowViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val viewModel by lazy { QuranViewModel(fixture.dependencies, wordLanguage = "en") }

    // Ayah 1:1 has a third word that the print puts on page 2, after the page break; 2:2 is on
    // page 3, two pages away from page 1.
    init {
        fixture.text.pages[2] =
            PageLayout(
                2,
                ScriptId.QpcHafs,
                listOf(
                    PageLine(
                        1,
                        listOf(
                            LineItem.WordItem(ReadFixture.word(1, 1, 3, "ثالث")),
                            LineItem.WordItem(ReadFixture.word(2, 1, 1, "الم")),
                        ),
                    ),
                ),
            )
        fixture.text.pages[3] =
            PageLayout(
                3,
                ScriptId.QpcHafs,
                listOf(PageLine(1, listOf(LineItem.WordItem(ReadFixture.word(2, 2, 1, "ذلك"))))),
            )
        fixture.text.pageByAyah[AyahRef(2, 2)] = 3
    }

    private fun startReciting() {
        viewModel.open(QuranTarget.Page(1))
        viewModel.enterReciteMode()
    }

    private val wordOnPageOne = WordKey(1, 1, 2)
    private val wordAfterThePageBreak = WordKey(1, 1, 3)
    private val firstWordOfNextSurah = WordKey(2, 1, 1)
    private val wordTwoPagesOn = WordKey(2, 2, 1)

    @Test
    fun aWordOnTheShownPageLeavesThePage() = runTest {
        startReciting()

        viewModel.followRecitation(wordOnPageOne)

        assertEquals(1, viewModel.state.value.currentPage)
        assertEquals(0, viewModel.state.value.animatedTurnToken)
    }

    @Test
    fun aWordOnTheNextPageTurnsWithTheAnimatedTurn() = runTest {
        startReciting()
        val jumpsBefore = viewModel.state.value.jumpToken

        viewModel.followRecitation(firstWordOfNextSurah)

        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(1, viewModel.state.value.animatedTurnToken)
        assertEquals(jumpsBefore, viewModel.state.value.jumpToken)
    }

    @Test
    fun anAyahOverAPageBreakTurnsAtTheWordWhereThePageBreaks() = runTest {
        startReciting()

        viewModel.followRecitation(wordOnPageOne)
        assertEquals(1, viewModel.state.value.currentPage)

        viewModel.followRecitation(wordAfterThePageBreak)
        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun reciteStartedAwayFromTheShownPageJumpsInsteadOfSliding() = runTest {
        startReciting()
        val jumpsBefore = viewModel.state.value.jumpToken

        viewModel.followRecitation(wordTwoPagesOn)

        assertEquals(3, viewModel.state.value.currentPage)
        assertEquals(jumpsBefore + 1, viewModel.state.value.jumpToken)
        assertEquals(0, viewModel.state.value.animatedTurnToken)
    }

    @Test
    fun aReaderWhoSwipedBackIsNotPulledToTheRecitedPage() = runTest {
        startReciting()
        viewModel.onPagerDragged()

        viewModel.followRecitation(firstWordOfNextSurah)

        assertEquals(1, viewModel.state.value.currentPage)
        assertEquals(0, viewModel.state.value.animatedTurnToken)
    }

    @Test
    fun reciteAWordOnTheShownPageAfterSwipingBackFollowsAgain() = runTest {
        startReciting()
        viewModel.onPagerDragged()
        viewModel.followRecitation(firstWordOfNextSurah)

        viewModel.followRecitation(wordOnPageOne)
        viewModel.followRecitation(firstWordOfNextSurah)

        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun goingToAPageByHandAlsoPausesTheFollow() = runTest {
        startReciting()
        viewModel.jumpToPage(1)

        viewModel.followRecitation(firstWordOfNextSurah)

        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun startingReciteAgainClearsTheOldPause() = runTest {
        startReciting()
        viewModel.onPagerDragged()
        viewModel.exitReciteMode()

        viewModel.enterReciteMode()
        viewModel.followRecitation(firstWordOfNextSurah)

        assertEquals(2, viewModel.state.value.currentPage)
    }

    @Test
    fun noPageTurnsWhenNotReciting() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.followRecitation(firstWordOfNextSurah)

        assertEquals(1, viewModel.state.value.currentPage)
    }
}
