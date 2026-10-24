package app.awn.quran.feature.learn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WordPagerPlanTest {

    @Test
    fun aWordOpenedFromASetSwipesOverTheWordsOfThatSet() {
        val plan = wordPagerPlan(rank = 25, from = LearnRoute.SetPage(3), formCount = 500)

        assertEquals((21..30).toList(), plan.ranks)
        assertEquals(4, plan.startIndex)
    }

    @Test
    fun theFirstWordOfASetHasNoPreviousWord() {
        val plan = wordPagerPlan(rank = 21, from = LearnRoute.SetPage(3), formCount = 500)

        assertEquals(0, plan.startIndex)
        assertEquals(21, plan.ranks.first())
    }

    @Test
    fun theLastWordOfASetHasNoNextWord() {
        val plan = wordPagerPlan(rank = 30, from = LearnRoute.SetPage(3), formCount = 500)

        assertEquals(plan.ranks.lastIndex, plan.startIndex)
        assertEquals(30, plan.ranks.last())
    }

    @Test
    fun aSetCutShortByTheWordListEndsAtTheLastForm() {
        val plan = wordPagerPlan(rank = 43, from = LearnRoute.SetPage(5), formCount = 43)

        assertEquals((41..43).toList(), plan.ranks)
    }

    @Test
    fun aWordOpenedFromAnotherPlaceStandsAlone() {
        val fromList = wordPagerPlan(rank = 25, from = null, formCount = 500)
        val fromPage = wordPagerPlan(rank = 25, from = LearnRoute.PageWords(7), formCount = 500)

        assertEquals(listOf(25), fromList.ranks)
        assertEquals(listOf(25), fromPage.ranks)
        assertEquals(0, fromList.startIndex)
    }

    @Test
    fun aWordOutsideTheSetItWasOpenedFromStandsAlone() {
        val plan = wordPagerPlan(rank = 99, from = LearnRoute.SetPage(3), formCount = 500)

        assertEquals(listOf(99), plan.ranks)
    }
}
