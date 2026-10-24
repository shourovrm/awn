package app.awn.quran.feature.duas.state

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.DuasFixtures
import app.awn.quran.feature.duas.DuasTestEnvironment
import app.awn.quran.feature.duas.PronunciationState
import app.awn.quran.feature.duas.content.DuaMatchField
import app.awn.quran.feature.duas.content.DuaSearchEntry
import app.awn.quran.feature.duas.content.DuaSearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuasViewModelTest {

    private val searchedEntries = mutableListOf<List<DuaSearchEntry>>()

    @Before
    fun useTestMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreMain() {
        Dispatchers.resetMain()
    }

    private fun viewModel(environment: DuasTestEnvironment = DuasTestEnvironment()) =
        DuasViewModel(
            dependencies = environment.dependencies(),
            search = { _, entries ->
                searchedEntries += entries
                listOf(DuaSearchHit(DuasFixtures.d3, DuaMatchField.Translation))
            },
            workDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun theCollectionAndTheCardsAreReadAtStart() {
        val state = viewModel().state.value

        assertEquals(DuasFixtures.catalogue, state.catalogue)
        assertEquals(setOf("d1", "d2", "d3"), state.cards.keys)
        val card = state.cards.getValue("d1")
        assertEquals("Al-Baqarah 2:201", card.reference)
        assertEquals("Our Lord, give us good in this world.", card.translation)
        assertEquals(listOf(false, false, false, true, true, true), card.words.map { it.isDuaWord })
    }

    @Test
    fun aDuaOverTwoAyahsShowsBothTranslationsAndARange() {
        val card = viewModel().state.value.cards.getValue("d2")

        assertEquals("Taha 20:25–26", card.reference)
        assertEquals(
            "He said, My Lord, expand for me my chest. And ease for me my task.",
            card.translation,
        )
    }

    @Test
    fun withNoTranslationTickedTheCardHasNoTranslation() {
        val environment = DuasTestEnvironment(translationsTicked = emptyList())

        val state = viewModel(environment).state.value

        assertNull(state.cards.getValue("d1").translation)
    }

    @Test
    fun theFirstTickedTranslationIsTheOneShown() {
        val environment =
            DuasTestEnvironment(
                translationsTicked =
                    listOf(TranslationId("bn_missing"), DuasFixtures.englishTranslation),
            )

        // The first ticked pack has no text for the ayah, so nothing is shown rather than a
        // second translation the user did not put first.
        assertNull(viewModel(environment).state.value.cards.getValue("d1").translation)
    }

    @Test
    fun aQueryAsksTheSearchWithOneEntryForEachDua() {
        val model = viewModel()

        model.setQuery("parents")

        val entries = searchedEntries.single()
        assertEquals(listOf("d1", "d2", "d3"), entries.map { it.dua.id })
        assertEquals("Parents and family", entries.last().groupName)
        assertEquals("Our Lord, forgive me and my parents.", entries.last().translation)
        assertEquals(listOf(DuasFixtures.d3), model.state.value.searchHits.map { it.dua })
    }

    @Test
    fun searchEntriesCarryTheGroupNameInTheAppLanguage() {
        val model = viewModel()
        model.onLanguageChanged("bn")

        model.setQuery("মা")

        assertEquals("মা-বাবা ও পরিবার", searchedEntries.last().last().groupName)
    }

    @Test
    fun aBlankQueryHasNoHits() {
        val model = viewModel()
        model.setQuery("parents")

        model.setQuery("  ")

        assertTrue(model.state.value.searchHits.isEmpty())
    }

    @Test
    fun aGroupOpensItsDuasNarrowedByTheFilter() {
        val model = viewModel()

        model.openGroup("good")
        assertEquals(listOf("d1", "d2"), model.state.value.deck?.duaIds)

        model.closeDeck()
        model.setFilter(DuasFilter.Prophets)
        model.openGroup("good")
        assertEquals(listOf("d2"), model.state.value.deck?.duaIds)
    }

    @Test
    fun theStartersOpenWithNoGroup() {
        val model = viewModel()

        model.openStarters()

        val deck = checkNotNull(model.state.value.deck)
        assertNull(deck.groupKey)
        assertEquals(listOf("d1"), deck.duaIds)
    }

    @Test
    fun aSearchResultOpensItsGroupAtThatDua() {
        val model = viewModel()

        model.openSearchResult(DuasFixtures.d2)

        val deck = checkNotNull(model.state.value.deck)
        assertEquals("good", deck.groupKey)
        assertEquals(listOf("d1", "d2"), deck.duaIds)
        assertEquals(1, deck.startIndex)
        assertEquals(listOf(DuasFixtures.d1, DuasFixtures.d2), model.state.value.deckDuas)
    }

    @Test
    fun savingAndCleanRecitationsReachTheState() {
        val model = viewModel()

        model.toggleSaved(DuasFixtures.d1)
        repeat(3) { model.recordCleanRecitation(DuasFixtures.d1) }

        val progress = model.state.value.progress
        assertTrue(progress.isSaved("d1"))
        assertTrue(progress.isLearned("d1"))
    }

    @Test
    fun wordTilesUseTheAppLanguageThenEnglishAndTheirSounds() {
        val model = viewModel()

        model.requestWordTiles(DuasFixtures.d1)

        val tiles = model.state.value.wordTiles.getValue("d1")
        assertEquals(
            listOf(WordKey(2, 201, 4), WordKey(2, 201, 5), WordKey(2, 201, 6)),
            tiles.map { it.key },
        )
        assertEquals(listOf("Our Lord", "give us", "in"), tiles.map { it.meaning })
        assertEquals(listOf("rabbana", "atina", "fi"), tiles.map { it.transliteration })
    }

    @Test
    fun tilesReadBeforeThePronunciationArrivesAreReadAgainWithIt() {
        val environment = DuasTestEnvironment()
        environment.pronunciation.value = PronunciationState.Missing
        val transliterations = environment.translations.wordTransliterations
        val sounds = transliterations.getValue(AyahRef(2, 201))
        transliterations.remove(AyahRef(2, 201))
        val model = viewModel(environment)
        model.requestWordTiles(DuasFixtures.d1)
        assertEquals(
            listOf("", "", ""),
            model.state.value.wordTiles.getValue("d1").map { it.transliteration },
        )

        transliterations[AyahRef(2, 201)] = sounds
        environment.pronunciation.value = PronunciationState.Installed

        assertEquals(
            listOf("rabbana", "atina", "fi"),
            model.state.value.wordTiles.getValue("d1").map { it.transliteration },
        )
    }

    @Test
    fun aBlankBanglaMeaningShowsTheEnglishOne() {
        val environment = DuasTestEnvironment()
        environment.translations.wordMeanings["bn" to AyahRef(2, 201)] =
            listOf("", "", "", "আমাদের রব", "", "")
        val model = viewModel(environment)
        model.onLanguageChanged("bn")

        model.requestWordTiles(DuasFixtures.d1)

        assertEquals(
            listOf("আমাদের রব", "give us", "in"),
            model.state.value.wordTiles.getValue("d1").map { it.meaning },
        )
    }

    @Test
    fun thePageIsThatOfTheFirstAyah() = runTest {
        assertEquals(313, viewModel().pageOf(DuasFixtures.d2))
    }
}
