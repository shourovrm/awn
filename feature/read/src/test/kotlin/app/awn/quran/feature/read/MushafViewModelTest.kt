package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.read.page.MushafViewModel
import app.awn.quran.feature.read.page.PageLoad
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MushafViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    // Built on first use so that the main dispatcher rule is already active.
    private val viewModel by lazy { MushafViewModel(fixture.dependencies) }

    @Test
    fun startWithoutRecentPageOpensPageOneAndReportsIt() = runTest {
        viewModel.start(null)

        val state = viewModel.state.value
        assertEquals(1, state.currentPage)
        assertEquals(1, state.juz)
        assertEquals("Al-Fatihah", state.headerSurah?.nameSimple)
        assertTrue(state.pages[1] is PageLoad.Loaded)
        assertEquals(listOf(1), fixture.progress.openedPages)
        assertEquals(1, fixture.bookmarks.recentPages().first().single().page)
    }

    @Test
    fun settlingOnAnotherPageUpdatesHeaderAndReports() = runTest {
        viewModel.start(1)
        viewModel.onPageSettled(2)

        val state = viewModel.state.value
        assertEquals(2, state.currentPage)
        assertEquals("Al-Baqarah", state.headerSurah?.nameSimple)
        assertEquals(listOf(1, 2), fixture.progress.openedPages)
    }

    @Test
    fun settlingOnTheSamePageReportsOnce() = runTest {
        viewModel.onPageSettled(1)
        viewModel.onPageSettled(1)

        assertEquals(listOf(1), fixture.progress.openedPages)
    }

    @Test
    fun aPageWithoutAnyLayoutFails() = runTest {
        viewModel.onPageSettled(4)

        assertTrue(viewModel.state.value.pages[4] is PageLoad.Failed)
    }

    @Test
    fun tappingAWordSelectsItAndOpensTheTranslationsOfItsAyah() = runTest {
        viewModel.start(1)
        val key = WordKey(1, 1, 2)

        viewModel.onWordTapped(key)

        val state = viewModel.state.value
        assertEquals(key, state.selectedWord)
        val sheet = assertNotNull(state.sheet).let { state.sheet!! }
        assertEquals(AyahRef(1, 1), sheet.ayah)
        assertEquals(listOf("In the name of Allah"), sheet.translations.map { it.text })
        assertEquals("Saheeh International", sheet.translations.single().name)
    }

    @Test
    fun tappingTheSameWordAgainClosesTheSheet() = runTest {
        viewModel.start(1)
        val key = WordKey(1, 1, 1)
        viewModel.onWordTapped(key)
        viewModel.onWordTapped(key)

        assertNull(viewModel.state.value.selectedWord)
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun changingPageClosesTheSheet() = runTest {
        viewModel.start(1)
        viewModel.onWordTapped(WordKey(1, 1, 1))
        viewModel.onPageSettled(2)

        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun toggleBookmarkAddsThenRemovesTheCurrentPage() = runTest {
        viewModel.start(1)

        viewModel.toggleBookmark()
        assertTrue(viewModel.state.value.isBookmarked)
        assertEquals(listOf(1), fixture.bookmarks.bookmarks().first().map { it.page })

        viewModel.toggleBookmark()
        assertFalse(viewModel.state.value.isBookmarked)
    }

    @Test
    fun savingTheSheetAyahMarksItSaved() = runTest {
        viewModel.start(1)
        viewModel.onWordTapped(WordKey(1, 1, 1))

        viewModel.toggleSavedAyah()

        assertEquals(listOf(AyahRef(1, 1)), fixture.bookmarks.savedAyahs().first().map { it.ayah })
        assertTrue(viewModel.state.value.sheet!!.isSaved)
    }
}
