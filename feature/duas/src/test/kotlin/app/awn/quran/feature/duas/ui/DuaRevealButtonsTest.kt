package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.recite.ReciteHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuaRevealButtonsTest {

    private val firstWord = WordKey(1, 1, 1)
    private val secondWord = WordKey(1, 1, 2)

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
}
