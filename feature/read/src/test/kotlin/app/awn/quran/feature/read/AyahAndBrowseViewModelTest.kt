package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationText
import app.awn.quran.feature.read.ayah.AyahListSource
import app.awn.quran.feature.read.ayah.AyahListViewModel
import app.awn.quran.feature.read.browse.BrowseViewModel
import app.awn.quran.feature.read.browse.TransferStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AyahAndBrowseViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()

    @Test
    fun pageSourceListsItsAyahsAndLoadsBlocksWithEveryLine() = runTest {
        val viewModel = AyahListViewModel(fixture.dependencies, AyahListSource.Page(1), "en")
        val first = AyahRef(1, 1)

        viewModel.ensureBlock(first)

        val state = viewModel.state.value
        assertEquals(FATIHA_AYAHS, state.ayahs)
        val block = state.blocks.getValue(first)
        assertEquals(listOf("In (the) name", "(of) Allah"), block.words.map { it.meaning })
        assertEquals("t1 t2", block.transliteration)
        assertEquals(listOf("In the name of Allah"), block.translations.map { it.text })
        assertEquals(listOf(1), fixture.progress.openedPages)
    }

    @Test
    fun surahSourceListsEveryAyahOfTheSurah() = runTest {
        val viewModel = AyahListViewModel(fixture.dependencies, AyahListSource.Surah(1), "en")

        assertEquals(ReadFixture.FATIHA_VERSES, viewModel.state.value.ayahs.size)
        assertEquals("Al-Fatihah", viewModel.state.value.headerSurah?.nameSimple)
    }

    @Test
    fun mergedTranslationShowsOnlyAtItsFirstAyah() = runTest {
        val id = TranslationId("en_saheeh")
        val merged = { ayah: Int ->
            AyahRef(1, ayah) to TranslationText(AyahRef(1, ayah), 2, 3, "both")
        }
        fixture.translations.texts.getValue(id).putAll(listOf(merged(2), merged(3)))
        val viewModel = AyahListViewModel(fixture.dependencies, AyahListSource.Page(1), "en")

        viewModel.ensureBlock(AyahRef(1, 2))
        viewModel.ensureBlock(AyahRef(1, 3))

        val blocks = viewModel.state.value.blocks
        assertEquals("2-3", blocks.getValue(AyahRef(1, 2)).translations.single().rangeLabel)
        assertTrue(blocks.getValue(AyahRef(1, 3)).translations.isEmpty())
    }

    @Test
    fun playingAyahBecomesTheCurrentOne() = runTest {
        val viewModel = AyahListViewModel(fixture.dependencies, AyahListSource.Page(1), "en")

        fixture.player.play("husary", AyahRef(1, 2), null)

        assertEquals(AyahRef(1, 2), viewModel.state.value.currentAyah)
    }

    @Test
    fun browseParsesOnlyPagesOfTheBook() = runTest {
        val viewModel = BrowseViewModel(fixture.dependencies)

        assertEquals(12, viewModel.parsePage(" 12 "))
        assertNull(viewModel.parsePage("0"))
        assertNull(viewModel.parsePage("605"))
        assertNull(viewModel.parsePage("abc"))
    }

    @Test
    fun browseListsSurahsWithTheirStartPage() = runTest {
        val viewModel = BrowseViewModel(fixture.dependencies)

        val first = viewModel.state.value.surahs.first()
        assertEquals(1, first.chapter.number)
        assertEquals(1, first.page)
    }

    @Test
    fun exportAndImportReportTheirOutcome() = runTest {
        val viewModel = BrowseViewModel(fixture.dependencies)
        var written = ""

        viewModel.export { written = it }
        assertEquals("{}", written)
        assertEquals(TransferStatus.Exported, viewModel.state.value.transferStatus)

        viewModel.import { throw java.io.IOException("unreadable") }
        assertEquals(TransferStatus.ImportFailed, viewModel.state.value.transferStatus)

        viewModel.import { "{}" }
        assertEquals(TransferStatus.Imported, viewModel.state.value.transferStatus)
    }

    private companion object {
        val FATIHA_AYAHS = (1..ReadFixture.FATIHA_VERSES).map { AyahRef(1, it) }
    }
}
