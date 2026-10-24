package app.awn.quran.feature.duas.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.feature.duas.DuasFixtures
import app.awn.quran.feature.duas.card.CardRequest
import app.awn.quran.feature.duas.card.DuaCardLoader
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaMatchField
import app.awn.quran.feature.duas.content.DuaSearchHit
import app.awn.quran.feature.duas.data.DuaProgress
import app.awn.quran.feature.duas.state.DuasFilter
import app.awn.quran.feature.duas.state.DuasState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

/** Renders the Duas home in Awn and Black modes at font scales 1.0 and 1.3. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DuasHomeScreenTest(private val mode: ColourMode, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0} at {1}")
        fun combinations(): List<Array<Any>> =
            listOf(ColourMode.Awn, ColourMode.Black).flatMap { mode ->
                listOf(1.0f, 1.3f).map { scale -> arrayOf<Any>(mode, scale) }
            }
    }

    @get:Rule val composeRule = createComposeRule()

    private val openedGroups = mutableListOf<String>()
    private val openedResults = mutableListOf<Dua>()
    private val chosenFilters = mutableListOf<DuasFilter>()
    private val typedQueries = mutableListOf<String>()
    private var startersOpened = 0
    private var wentBack = 0

    private val cards = runBlocking {
        DuaCardLoader(DuasFixtures.quranText(), DuasFixtures.translations())
            .load(DuasFixtures.catalogue.duas, CardRequest(DuasFixtures.englishTranslation, "en"))
            .associateBy { it.dua.id }
    }

    private fun baseState() = DuasState(catalogue = DuasFixtures.catalogue, cards = cards)

    private fun show(state: DuasState, language: String = "en") {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AwnTheme(barBehindStatusBar = true) {
                    DuasHomeScreen(
                        state = state,
                        language = language,
                        actions =
                            DuasHomeActions(
                                onBack = { wentBack++ },
                                onQueryChange = { typedQueries += it },
                                onFilterChange = { chosenFilters += it },
                                onOpenStarters = { startersOpened++ },
                                onOpenGroup = { openedGroups += it },
                                onOpenResult = { openedResults += it },
                            ),
                    )
                }
            }
        }
    }

    @Test
    fun theTitleTheCountTheChipsAndTheStartersAreShown() {
        show(baseState())

        composeRule.onNodeWithText("Duas from the Quran").assertIsDisplayed()
        composeRule.onNodeWithText("3 duas").assertIsDisplayed()
        composeRule.onNodeWithText("Search duas").assertIsDisplayed()
        listOf("All", "Rabbana", "Rabbi", "Prophets' duas", "Saved", "Learned").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("Ten short duas to start").assertIsDisplayed()
        composeRule.onNodeWithText("3 min a day").assertIsDisplayed()
        composeRule.onNodeWithText("Groups").assertIsDisplayed()
    }

    @Test
    fun eachGroupShowsItsNameCountAndLearnedDuas() {
        val learned = DuaProgress(cleanRecitations = mapOf("d1" to 3))
        show(baseState().copy(progress = learned))

        composeRule.onNodeWithText("Good in this world and the next").assertIsDisplayed()
        composeRule.onNodeWithText("2 duas · 1 learned").assertIsDisplayed()
        composeRule.onNodeWithText("Parents and family").assertIsDisplayed()
        composeRule.onNodeWithText("1 dua").assertIsDisplayed()
    }

    @Test
    fun groupNamesFollowTheAppLanguage() {
        show(baseState(), language = "bn")

        composeRule.onNodeWithText("দুনিয়া ও আখিরাতের ভালো").assertIsDisplayed()
        composeRule.onNodeWithText("মা-বাবা ও পরিবার").assertIsDisplayed()
    }

    @Test
    fun tappingAGroupOpensIt() {
        show(baseState())

        composeRule.onNodeWithText("Parents and family").performClick()

        assertEquals(listOf("parents"), openedGroups)
    }

    @Test
    fun tappingTheStartersOpensThem() {
        show(baseState())

        composeRule.onNodeWithText("Ten short duas to start").performClick()

        assertEquals(1, startersOpened)
    }

    @Test
    fun aChipChoosesItsFilter() {
        show(baseState())

        composeRule.onNodeWithText("Saved").performClick()

        assertEquals(listOf(DuasFilter.Saved), chosenFilters)
    }

    @Test
    fun aFilterNarrowsTheCountsAndHidesEmptyGroups() {
        show(baseState().copy(filter = DuasFilter.Rabbi))

        // The bar and the one group left both say "1 dua".
        composeRule.onAllNodesWithText("1 dua").assertCountEquals(2)
        composeRule.onNodeWithText("Good in this world and the next").assertIsDisplayed()
        composeRule.onNodeWithText("Parents and family").assertDoesNotExist()
        composeRule.onNodeWithText("Ten short duas to start").assertDoesNotExist()
    }

    @Test
    fun aFilterWithNothingLeftSaysSo() {
        show(baseState().copy(filter = DuasFilter.Learned))

        composeRule.onNodeWithText("No duas here yet").assertIsDisplayed()
    }

    @Test
    fun typingInTheFieldReportsTheQuery() {
        show(baseState())

        composeRule.onNode(hasSetTextAction()).performTextInput("parents")

        assertEquals("parents", typedQueries.last())
    }

    @Test
    fun searchResultsShowTheReferenceTheArabicAndTheMarkedTranslation() {
        val hit = DuaSearchHit(DuasFixtures.d3, DuaMatchField.Translation)
        show(baseState().copy(query = "parents", searchHits = listOf(hit)))

        composeRule.onNodeWithText("Duas · 1").assertIsDisplayed()
        composeRule.onNodeWithText("Ibrahim 14:41 · Rabbana").assertIsDisplayed()
        composeRule.onNodeWithText("رَبَّنَا ٱغْفِرْ لِى").assertIsDisplayed()
        composeRule.onNodeWithText("Our Lord, forgive me and my parents.").assertIsDisplayed()
        // The groups and chips give way to the results.
        composeRule.onNodeWithText("Groups").assertDoesNotExist()
    }

    @Test
    fun tappingAResultOpensItsCard() {
        val hit = DuaSearchHit(DuasFixtures.d3, DuaMatchField.Translation)
        show(baseState().copy(query = "parents", searchHits = listOf(hit)))

        composeRule.onNodeWithText("Ibrahim 14:41 · Rabbana").performClick()

        assertEquals(listOf(DuasFixtures.d3), openedResults)
    }

    @Test
    fun aSearchWithNoHitsSaysSo() {
        show(baseState().copy(query = "xyz"))

        composeRule.onNodeWithText("No duas found").assertIsDisplayed()
    }

    @Test
    fun backLeavesTheCollection() {
        show(baseState())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, wentBack)
    }
}
