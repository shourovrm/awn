package app.awn.quran.feature.duas.ui

import androidx.compose.ui.graphics.Color
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnReciteFills
import app.awn.quran.feature.duas.DuasFixtures
import app.awn.quran.player.PlaybackState
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardHelpersTest {

    @Test
    fun repeatCyclesOneThreeFiveAndBack() {
        assertEquals(3, nextRepeatCount(1))
        assertEquals(5, nextRepeatCount(3))
        assertEquals(1, nextRepeatCount(5))
        assertEquals(1, nextRepeatCount(2))
    }

    @Test
    fun speedCyclesThroughTheFourSteps() {
        assertEquals(1.25f, nextSpeed(1f))
        assertEquals(1.5f, nextSpeed(1.25f))
        assertEquals(0.75f, nextSpeed(1.5f))
        assertEquals(1f, nextSpeed(0.75f))
        assertEquals(1f, nextSpeed(1.1f))
    }

    @Test
    fun speedTextHasNoTrailingZeros() {
        assertEquals("1", speedText(1f))
        assertEquals("1.25", speedText(1.25f))
        assertEquals("1.5", speedText(1.5f))
        assertEquals("0.75", speedText(0.75f))
    }

    @Test
    fun ayahNumbersUseArabicIndicDigits() {
        assertEquals("٢٠١", arabicIndicDigits(201))
        assertEquals("١", arabicIndicDigits(1))
    }

    @Test
    fun theFirstMatchIsMarkedWhateverTheCase() {
        val marked = markMatch("Forgive me and my Parents", "parents", Color.Yellow)

        assertEquals("Forgive me and my Parents", marked.text)
        val span = marked.spanStyles.single()
        assertEquals("Parents", marked.text.substring(span.start, span.end))
    }

    @Test
    fun aQueryWithNoMatchMarksNothing() {
        assertTrue(markMatch("Our Lord", "parents", Color.Yellow).spanStyles.isEmpty())
        assertTrue(markMatch("Our Lord", " ", Color.Yellow).spanStyles.isEmpty())
    }

    @Test
    fun aRecitationIsCleanOnlyWhenEveryDuaWordIsConfirmed() {
        val words = setOf(WordKey(1, 1, 1), WordKey(1, 1, 2))
        val allConfirmed = words.associateWith { WordState.Confirmed }

        assertTrue(isCleanRecitation(words, allConfirmed))
        assertFalse(
            isCleanRecitation(words, allConfirmed + (WordKey(1, 1, 2) to WordState.Tentative)),
        )
        assertFalse(isCleanRecitation(words, mapOf(WordKey(1, 1, 1) to WordState.Confirmed)))
        assertFalse(isCleanRecitation(emptySet(), emptyMap()))
    }

    @Test
    fun wordsOutsideTheDuaDoNotDecideWhetherARecitationIsClean() {
        val duaWords = setOf(WordKey(2, 201, 4))
        val states =
            mapOf(WordKey(2, 201, 1) to WordState.Wrong, WordKey(2, 201, 4) to WordState.Confirmed)

        assertTrue(isCleanRecitation(duaWords, states))
    }

    @Test
    fun reciteFillsFollowTheFieldAndLeaveUnmarkedWordsClear() {
        assertEquals(
            AwnReciteFills.dayConfirmed,
            reciteFill(WordState.Confirmed, isLightField = true),
        )
        assertEquals(AwnReciteFills.nightWrong, reciteFill(WordState.Wrong, isLightField = false))
        assertEquals(
            AwnReciteFills.dayTentative,
            reciteFill(WordState.Mispronounced, isLightField = true),
        )
        assertEquals(Color.Transparent, reciteFill(WordState.Neutral, isLightField = true))
        assertEquals(Color.Transparent, reciteFill(WordState.Hidden, isLightField = false))
    }

    @Test
    fun thePlayingWordIsFoundOnlyForTheDuasOwnAyahs() {
        val playing =
            PlaybackState.Playing(
                "alafasy",
                AyahRef(20, 26),
                wordPosition = 2,
                positionMs = 0,
                durationMs = null,
            )
        val other = PlaybackState.Playing("alafasy", AyahRef(1, 1), 1, 0, null)

        assertEquals(WordKey(20, 26, 2), playing.playingWordOf(DuasFixtures.d2))
        assertEquals(AyahRef(20, 26), playing.ayahOf(DuasFixtures.d2))
        assertNull(playing.playingWordOf(DuasFixtures.d1))
        assertNull(other.ayahOf(DuasFixtures.d2))
        assertNull(PlaybackState.Idle.ayahOf(DuasFixtures.d2))
    }

    @Test
    fun aPlayerThatDoesNotKnowTheWordHasNoPlayingWord() {
        val playing = PlaybackState.Playing("alafasy", AyahRef(20, 25), null, 0, null)

        assertNull(playing.playingWordOf(DuasFixtures.d2))
        assertEquals(AyahRef(20, 25), playing.ayahOf(DuasFixtures.d2))
    }
}
