package app.awn.quran.feature.read

import app.awn.quran.core.data.TranslationId
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ScreenOrientation
import app.awn.quran.feature.read.settings.TextLayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The layers menu, the per-layer text sizes and the rotate button of the Quran screen. */
class QuranLayersTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val viewModel by lazy { QuranViewModel(fixture.dependencies, wordLanguage = "en") }

    private suspend fun stored() = fixture.store.settings.first()

    @Test
    fun theLayersMenuListsTheTranslationsOnThePhone() = runTest {
        viewModel.open(QuranTarget.Page(1))

        val names = viewModel.state.value.installedTranslations.map { it.name }

        assertEquals(listOf("Saheeh International"), names)
    }

    @Test
    fun ticksAreSavedPerLayer() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.toggleLayer(TextLayer.Arabic)
        viewModel.toggleLayer(TextLayer.WordByWord)
        viewModel.toggleLayer(TextLayer.Transliteration)

        assertFalse(stored().showArabic)
        assertFalse(stored().showWordByWord)
        assertFalse(stored().showTransliteration)
    }

    @Test
    fun aTranslationIsTickedAndUntickedByName() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val saheeh = TranslationId("en_saheeh")

        viewModel.toggleTranslation(saheeh)
        assertFalse(saheeh in stored().translations)

        viewModel.toggleTranslation(saheeh)
        assertTrue(saheeh in stored().translations)
    }

    @Test
    fun theLastTickedLayerCannotBeUnticked() = runTest {
        fixture.store.update {
            it.copy(
                translations = emptyList(),
                showTransliteration = false,
                showWordByWord = false,
                showArabic = true,
            )
        }
        viewModel.open(QuranTarget.Page(1))

        viewModel.toggleLayer(TextLayer.Arabic)

        assertTrue(stored().showArabic)
    }

    @Test
    fun hidingTheArabicIsAllowedWhileAnotherLayerShows() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.toggleLayer(TextLayer.Arabic)

        assertFalse(stored().showArabic)
        assertEquals(PageView.Mushaf, stored().pageView)
    }

    @Test
    fun theTextSizeButtonMovesOneLayerAtATime() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val before = stored()

        viewModel.changeTextSize(TextLayer.WordByWord, 2)
        viewModel.changeTextSize(TextLayer.Arabic, -1)

        assertEquals(before.wordByWordSizeStep + 2, stored().wordByWordSizeStep)
        assertEquals(before.arabicSizeStep - 1, stored().arabicSizeStep)
        assertEquals(before.translationSizeStep, stored().translationSizeStep)
        assertEquals(before.transliterationSizeStep, stored().transliterationSizeStep)
    }

    @Test
    fun theMushafSizeIsSeparateFromTheLineByLineArabicSize() = runTest {
        viewModel.open(QuranTarget.Page(1))
        val arabicBefore = stored().arabicSizeStep

        viewModel.changeMushafSize(1)

        assertEquals(1, stored().mushafSizeStep)
        assertEquals(arabicBefore, stored().arabicSizeStep)
    }

    @Test
    fun theRotateButtonTurnsTheQuranPagesTheOtherWay() = runTest {
        viewModel.open(QuranTarget.Page(1))

        viewModel.rotate(isLandscapeNow = false)
        assertEquals(ScreenOrientation.Landscape, stored().quranOrientation)

        viewModel.rotate(isLandscapeNow = true)
        assertEquals(ScreenOrientation.Portrait, stored().quranOrientation)
        assertEquals(ScreenOrientation.Auto, stored().appOrientation)
    }
}
