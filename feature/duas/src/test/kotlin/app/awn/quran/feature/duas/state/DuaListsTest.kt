package app.awn.quran.feature.duas.state

import app.awn.quran.feature.duas.DuasFixtures
import app.awn.quran.feature.duas.content.LocalName
import app.awn.quran.feature.duas.data.CLEAN_RECITATIONS_TO_LEARN
import app.awn.quran.feature.duas.data.DuaProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class DuaListsTest {

    private val catalogue = DuasFixtures.catalogue
    private val noProgress = DuaProgress()

    @Test
    fun groupsKeepTheDataOrderAndNumberFromOne() {
        val rows = groupRows(catalogue, DuasFilter.All, noProgress)

        assertEquals(listOf("good", "parents"), rows.map { it.group.key })
        assertEquals(listOf(1, 2), rows.map { it.number })
        assertEquals(listOf(2, 1), rows.map { it.duaCount })
    }

    @Test
    fun learnedDuasAreCountedPerGroup() {
        val learned =
            DuaProgress(cleanRecitations = mapOf("d1" to CLEAN_RECITATIONS_TO_LEARN, "d2" to 2))

        val rows = groupRows(catalogue, DuasFilter.All, learned)

        assertEquals(listOf(1, 0), rows.map { it.learnedCount })
    }

    @Test
    fun theProphetFilterKeepsOnlyPropheticDuasAndDropsEmptyGroups() {
        val rows = groupRows(catalogue, DuasFilter.Prophets, noProgress)

        assertEquals(listOf("good", "parents"), rows.map { it.group.key })
        assertEquals(listOf(1, 1), rows.map { it.duaCount })
    }

    @Test
    fun theRabbiFilterLeavesOneGroup() {
        val rows = groupRows(catalogue, DuasFilter.Rabbi, noProgress)

        assertEquals(listOf("good"), rows.map { it.group.key })
        assertEquals(1, filteredDuaCount(catalogue, DuasFilter.Rabbi, noProgress))
    }

    @Test
    fun theSavedAndLearnedFiltersFollowTheProgress() {
        val progress = DuaProgress(savedIds = setOf("d3"), cleanRecitations = mapOf("d1" to 3))

        assertEquals(
            listOf("d3"),
            duasOfGroup(catalogue, "parents", DuasFilter.Saved, progress).map { it.id },
        )
        assertEquals(
            listOf("d1"),
            duasOfGroup(catalogue, "good", DuasFilter.Learned, progress).map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            groupRows(catalogue, DuasFilter.Learned, noProgress).map { it.group.key },
        )
    }

    @Test
    fun startersAreNarrowedByTheFilterToo() {
        assertEquals(listOf("d1"), starterDuas(catalogue, DuasFilter.All, noProgress).map { it.id })
        assertEquals(
            emptyList<String>(),
            starterDuas(catalogue, DuasFilter.Prophets, noProgress).map { it.id },
        )
    }

    @Test
    fun aNameFollowsTheAppLanguageAndFallsBackToEnglish() {
        val name = LocalName("Parents", "মা-বাবা")

        assertEquals("মা-বাবা", name.inLanguage("bn"))
        assertEquals("Parents", name.inLanguage("en"))
        assertEquals("Parents", LocalName("Parents", "").inLanguage("bn"))
    }
}
