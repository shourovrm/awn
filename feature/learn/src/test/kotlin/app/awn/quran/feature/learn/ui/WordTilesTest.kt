package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.LessonBlock
import app.awn.quran.feature.learn.course.WordMark
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import app.awn.quran.feature.learn.sound.UnitSounds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Word tiles: a tap plays the word, Play all plays every word, the surah name opens the ayah. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordTilesTest {

    @get:Rule val composeRule = createComposeRule()

    private val playedWords = mutableListOf<WordKey>()
    private val openedAyahs = mutableListOf<AyahRef>()

    private val sounds =
        object : UnitSounds {
            override suspend fun playClips(clipIds: List<String>, speed: Float) = false

            override suspend fun playWord(word: WordKey) {
                playedWords += word
            }

            override fun stop() = Unit
        }

    private val block =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"tajweed-15",
            "course":"tajweed","order":15,"introducedBy":[],"licence":"x","reviewed":false,
            "languages":{"en":{"title":"Nasal sound","screens":[
            {"kind":"screen","title":"Held","blocks":[
              {"type":"words","mark":"ghunnah","items":[
                {"key":"2:6:1","caption":"inna","letters":[2]},
                {"key":"78:1:1","caption":"amma","letters":[]}]},
              {"type":"tip","text":"Pinch your nose."}]}]}}}]}""",
            )
            .lessons
            .single()
            .text("en")
            .screens
            .single()
            .blocks

    private fun show() {
        composeRule.setContent {
            AwnTheme {
                CompositionLocalProvider(
                    LocalUnitSounds provides sounds,
                    LocalOpenAyah provides { ayah -> openedAyahs += ayah },
                ) {
                    LessonBlocks(block)
                }
            }
        }
    }

    @Test
    fun theWordsBlockKeepsItsMarkAndLetters() {
        val words = block.first() as LessonBlock.Words

        assertEquals(WordMark.Ghunnah, words.mark)
        assertEquals(listOf(2), words.items.first().letters)
        assertEquals(LessonBlock.Tip("Pinch your nose."), block[1])
    }

    @Test
    fun tappingATilePlaysItsWord() {
        show()

        composeRule.onNodeWithText("amma").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(WordKey(78, 1, 1)), playedWords)
    }

    @Test
    fun playAllPlaysEveryWordInOrder() {
        show()

        composeRule.onNodeWithText("Play all").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(WordKey(2, 6, 1), WordKey(78, 1, 1)), playedWords)
    }

    @Test
    fun theAyahLinkOpensTheAyahOfTheWord() {
        show()

        composeRule.onAllNodesWithTag(WORD_TILE_AYAH_TAG)[1].performClick()

        assertEquals(listOf(AyahRef(78, 1)), openedAyahs)
    }

    @Test
    fun theTipIsShown() {
        show()

        composeRule.onNodeWithText("Pinch your nose.").assertExists()
    }
}
