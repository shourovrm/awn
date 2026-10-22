package app.awn.quran.search

import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The search screen shows the surahs whose name matches above the ayah results. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelSurahTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun useTestMain() = Dispatchers.setMain(dispatcher)

    @After fun restoreMain() = Dispatchers.resetMain()

    private val chapters =
        listOf(
            Chapter(1, "الفاتحة", "Al-Fatihah", "আল ফাতিহা", 7, "makkah", false),
            Chapter(2, "البقرة", "Al-Baqarah", "আল বাকারা", 286, "madinah", true),
        )

    private fun viewModel() =
        SearchViewModel(
            search = FakeSearchRepository(),
            quranText = FakeQuranTextRepository(chapterList = chapters),
            script = ScriptId.V2,
        )

    @Test
    fun aSurahNameQueryListsThatSurah() =
        runTest(dispatcher) {
            val model = viewModel()
            val collector = launch { model.state.collect {} }

            model.setQuery("baqara")
            advanceUntilIdle()

            assertEquals(listOf(2), model.state.value.surahs.map { it.number })
            collector.cancel()
        }

    @Test
    fun anEmptyQueryListsNoSurah() =
        runTest(dispatcher) {
            val model = viewModel()
            val collector = launch { model.state.collect {} }

            model.setQuery("")
            advanceUntilIdle()

            assertTrue(model.state.value.surahs.isEmpty())
            collector.cancel()
        }
}
