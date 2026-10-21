package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.quran.PageContentLoader
import app.awn.quran.feature.read.quran.PageItem
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.quran.meaningsWithEnglishFallback
import app.awn.quran.feature.read.settings.PageView
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The word meanings come from the chosen language, with English where that language has none. */
class WordLanguageFallbackTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val ayah = AyahRef(1, 1)

    private suspend fun meaningsFor(language: String): List<String?> =
        PageContentLoader(fixture.text, fixture.translations, wordLanguage = language)
            .loadBlock(ayah, emptyList())
            .words
            .map { it.meaning }

    @Test
    fun theChosenLanguageIsUsedWhenItsPackIsOnThePhone() = runTest {
        fixture.translations.wordMeanings["bn" to ayah] = listOf("নামে", "আল্লাহর")

        assertEquals(listOf("নামে", "আল্লাহর"), meaningsFor("bn"))
    }

    @Test
    fun aLanguageWithNoPackShowsTheEnglishMeanings() = runTest {
        assertEquals(listOf("In (the) name", "(of) Allah"), meaningsFor("ur"))
    }

    @Test
    fun aBlankRowShowsTheEnglishMeaningOfThatWordOnly() = runTest {
        fixture.translations.wordMeanings["bn" to ayah] = listOf("", "আল্লাহর")

        assertEquals(listOf("In (the) name", "আল্লাহর"), meaningsFor("bn"))
    }

    @Test
    fun aMissingEnglishMeaningStaysBlank() {
        assertEquals(
            listOf("a", ""),
            meaningsWithEnglishFallback(wanted = listOf("a", ""), english = listOf("x")),
        )
    }

    @Test
    fun theScreenFollowsTheAppLanguageUntilALanguageIsChosen() = runTest {
        fixture.translations.wordMeanings["bn" to ayah] = listOf("নামে", "আল্লাহর")
        val viewModel = QuranViewModel(fixture.dependencies, wordLanguage = "bn")
        fixture.store.update { it.copy(pageView = PageView.LineByLine) }
        viewModel.open(QuranTarget.Page(1))
        assertEquals("নামে", firstMeaning(viewModel))

        fixture.store.update { it.copy(wordByWordLanguage = "en") }
        assertEquals("In (the) name", firstMeaning(viewModel))

        fixture.store.update { it.copy(wordByWordLanguage = null) }
        assertEquals("নামে", firstMeaning(viewModel))

        viewModel.onAppLanguageChanged("en")
        assertEquals("In (the) name", firstMeaning(viewModel))
    }

    private fun firstMeaning(viewModel: QuranViewModel): String? =
        viewModel.state.value.contents[1]
            ?.items
            ?.filterIsInstance<PageItem.Ayah>()
            ?.first()
            ?.block
            ?.words
            ?.first()
            ?.meaning
}
