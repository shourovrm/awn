package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.FakeDuaRecitation
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaOpening
import app.awn.quran.feature.recite.ReciteHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuaRevealButtonsTest {

    private val firstWord = WordKey(1, 1, 1)
    private val secondWord = WordKey(1, 1, 2)

    // The dua covers words 3 to 5 of 7:23; words 1 and 2 are before it.
    private val dua =
        Dua(
            id = "test-dua",
            start = WordKey(7, 23, 3),
            end = WordKey(7, 23, 5),
            groupKey = "good",
            opening = DuaOpening.Rabbana,
            prophetKey = null,
            isStarter = false,
        )
    private val wordBeforeDua = WordKey(7, 23, 2)
    private val wordOfDua = WordKey(7, 23, 3)
    private val wordAfterDua = WordKey(7, 23, 6)

    @Test
    fun firstTapShowsTheLetterWhenTheSettingIsOnAndNoLetterIsShown() {
        val state =
            duaRevealStateOf(showsFirstLetterFirst = true, hint = null, currentWord = firstWord)

        assertTrue(state.nextTapShowsLetter)
    }

    @Test
    fun secondTapShowsTheWordOnceTheLetterOfTheCurrentWordIsShown() {
        val hint = ReciteHint(firstWord, "ا")

        val state =
            duaRevealStateOf(showsFirstLetterFirst = true, hint = hint, currentWord = firstWord)

        assertFalse(state.nextTapShowsLetter)
    }

    @Test
    fun aLetterOfAnotherWordDoesNotStopTheLetterStepForTheCurrentWord() {
        val hint = ReciteHint(firstWord, "ا")

        val state =
            duaRevealStateOf(showsFirstLetterFirst = true, hint = hint, currentWord = secondWord)

        assertTrue(state.nextTapShowsLetter)
    }

    @Test
    fun theWordIsShownAtOnceWhenTheSettingIsOff() {
        val state =
            duaRevealStateOf(showsFirstLetterFirst = false, hint = null, currentWord = firstWord)

        assertFalse(state.nextTapShowsLetter)
        assertFalse(state.showsFirstLetterFirst)
    }

    @Test
    fun hintLettersMapTheHintWordToItsLetter() {
        val hint = ReciteHint(secondWord, "ب")

        assertEquals(mapOf(secondWord to "ب"), hintLettersOf(hint))
    }

    @Test
    fun hintLettersAreEmptyWithoutAHint() {
        assertEquals(emptyMap<WordKey, String>(), hintLettersOf(null))
    }

    @Test
    fun aWordBeforeTheDuaIsBeforeTheDua() {
        assertTrue(isBeforeDua(wordBeforeDua, dua))
    }

    @Test
    fun theDuasFirstWordAndWordsAfterItAreNotBeforeTheDua() {
        assertFalse(isBeforeDua(wordOfDua, dua))
        assertFalse(isBeforeDua(wordAfterDua, dua))
    }

    @Test
    fun noCurrentWordIsNotBeforeTheDua() {
        assertFalse(isBeforeDua(null, dua))
    }

    @Test
    fun revealWordsBeforeDuaRevealsOnlyTheWordsBeforeTheDuaWholeWord() {
        val recitation = FakeDuaRecitation()
        val hiddenWords = listOf(WordKey(7, 23, 1), wordBeforeDua, wordOfDua, wordAfterDua)
        recitation.setHiddenWordsInOrder(hiddenWords)

        revealWordsBeforeDua(recitation, dua, cardWordCount = 4)

        assertEquals(listOf(false, false), recitation.revealWordCalls)
        assertEquals(wordOfDua, recitation.currentWord.value)
    }

    @Test
    fun revealWordsBeforeDuaDoesNothingWhenTheDuaIsAlreadyTheCurrentWord() {
        val recitation = FakeDuaRecitation()
        recitation.setHiddenWordsInOrder(listOf(wordOfDua, wordAfterDua))

        revealWordsBeforeDua(recitation, dua, cardWordCount = 4)

        assertEquals(emptyList<Boolean>(), recitation.revealWordCalls)
    }

    @Test
    fun revealWordsBeforeDuaStopsAtTheCardWordCountWhenTheCurrentWordNeverMoves() {
        val recitation = FakeDuaRecitation()
        recitation.currentWord.value = wordBeforeDua

        revealWordsBeforeDua(recitation, dua, cardWordCount = 4)

        assertEquals(4, recitation.revealWordCalls.size)
    }
}
