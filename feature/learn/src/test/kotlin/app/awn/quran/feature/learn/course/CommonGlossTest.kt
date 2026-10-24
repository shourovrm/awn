package app.awn.quran.feature.learn.course

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeTranslationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The counts below are the real glosses of the first 40 occurrences of each form in wbw_en and
 * wbw_bn (measured 2026-10-24).
 */
class CommonGlossTest {

    private fun glosses(vararg countsByGloss: Pair<String, Int>): List<String> =
        countsByGloss.flatMap { (gloss, count) ->
            List(count) { gloss }
        }

    @Test
    fun theFormOfAllahShowsAllahNotOf() {
        val glosses =
            glosses(
                "(of) Allah" to 6,
                "Allah" to 26,
                "in Allah" to 4,
                "And Allah" to 1,
                "but Allah" to 1,
                "(is) Allah" to 2,
            )

        // "(of) Allah", "(is) Allah" and "Allah" are one vote of 34; "in Allah" has 4.
        assertEquals("Allah", CommonGloss.of(glosses))
    }

    @Test
    fun theFormOfIlaShowsToNotWith() {
        val glosses =
            glosses(
                "with" to 3,
                "to" to 16,
                "for" to 4,
                "till" to 1,
                "[to]" to 2,
                "[towards]" to 2,
                "towards" to 2,
                "at" to 3,
                "(is) with" to 1,
                "until" to 1,
                "and to" to 1,
            )

        assertEquals("to", CommonGloss.of(glosses))
    }

    @Test
    fun theFormOfAlaShowsOnNotAreOn() {
        val glosses =
            glosses(
                "(are) on" to 3,
                "on" to 10,
                "and on" to 2,
                "(is) on" to 3,
                "to" to 4,
                "over" to 6,
                "upon" to 2,
                "[on]" to 1,
            )

        assertEquals("on", CommonGloss.of(glosses))
    }

    @Test
    fun aBanglaGlossLosesItsBracketedEnding() {
        val glosses =
            glosses(
                "মধ্যে আছে" to 1,
                "মধ্যে" to 26,
                "মধ্যে (আছে)" to 3,
                "মধ্যে (থাকবে)" to 1,
                "ব্যাপারে" to 3,
                "সম্পর্কে" to 1,
            )

        assertEquals("মধ্যে", CommonGloss.of(glosses))
    }

    @Test
    fun aTieGoesToTheGlossThatAppearsFirst() {
        assertEquals("said", CommonGloss.of(listOf("said", "he said", "said", "he said")))
    }

    @Test
    fun onlyTheBracketsAtTheEdgesAreDropped() {
        // Brackets inside a phrase are kept: "the Aad" would be a different phrase.
        assertEquals("the (people of) Aad", CommonGloss.of(listOf("the (people of) Aad")))
        assertEquals("the path", CommonGloss.of(listOf("(to) the path")))
        assertEquals("the path", CommonGloss.of(listOf("the path (of)")))
    }

    @Test
    fun glossesWithNothingOutsideBracketsDoNotVote() {
        assertNull(CommonGloss.of(listOf("(is)", "[the]", "")))
        assertEquals("on", CommonGloss.of(listOf("(is)", "[the]", "on")))
    }

    @Test
    fun theMeaningOfAFormFollowsTheGlossesOfItsPlaces() = runBlocking {
        val places = (1..6).map { WordKey(2, it, 1) }
        val translations = FakeTranslationRepository()
        // The first place, the form's representative, carries the odd gloss "with".
        val glossesByPlace = listOf("with", "to", "to", "(is) to", "for", "to")
        places.zip(glossesByPlace).forEach { (key, gloss) ->
            translations.wordMeanings["en" to AyahRef(key.surah, key.ayah)] = listOf(gloss)
        }
        val meanings = RepositoryWordMeanings(translations, "en")

        assertEquals("to", meanings.commonMeaning(places))
    }

    @Test
    fun aMissingPackFallsBackToEnglishForEachPlace() = runBlocking {
        val places = listOf(WordKey(2, 1, 1), WordKey(2, 2, 1))
        val translations = FakeTranslationRepository()
        places.forEach { translations.wordMeanings["en" to AyahRef(2, it.ayah)] = listOf("to") }
        val meanings = RepositoryWordMeanings(translations, "bn")

        assertEquals("to", meanings.commonMeaning(places))
    }

    @Test
    fun noInstalledPackGivesNoMeaning() = runBlocking {
        val meanings = RepositoryWordMeanings(FakeTranslationRepository(), "en")

        assertNull(meanings.commonMeaning(listOf(WordKey(2, 1, 1))))
    }

    @Test
    fun onlyTheFirstFortyPlacesAreRead() = runBlocking {
        val translations = FakeTranslationRepository()
        val places = (1..100).map { WordKey(2, it, 1) }
        // Places 1 to 40 say "to" 21 times; the 60 after them say "for" and must not be read.
        places.forEach {
            val gloss =
                when {
                    it.ayah <= 21 -> "to"
                    it.ayah <= 40 -> "at"
                    else -> "for"
                }
            translations.wordMeanings["en" to AyahRef(2, it.ayah)] = listOf(gloss)
        }
        val meanings = RepositoryWordMeanings(translations, "en")

        assertEquals("to", meanings.commonMeaning(places))
    }
}
