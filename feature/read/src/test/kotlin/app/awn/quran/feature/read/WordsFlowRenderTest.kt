package app.awn.quran.feature.read

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.quran.AyahBlock
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.ListPageEnvironment
import app.awn.quran.feature.read.quran.PageContent
import app.awn.quran.feature.read.quran.PageItem
import app.awn.quran.feature.read.quran.PageListView
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The words of the line view as the user meets them: a word-by-word cell shows its meaning and a
 * tap hands the screen that word, and a Hidden word shows only its hint letter. These pin the
 * behaviour the dua cards will share, so the move to a smaller input changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordsFlowRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val ayah = AyahRef(1, 2)
    private val firstWord = AyahWord(WordKey(1, 2, 1), "ٱلْحَمْدُ", "All praise", "al-ḥamdu")
    private val secondWord = AyahWord(WordKey(1, 2, 2), "لِلَّهِ", "(is) for Allah", "lillahi")

    private val wordByWord =
        ReadSettings(
            script = ScriptId.QpcHafs,
            pageView = PageView.LineByLine,
            showWordByWord = true,
            translations = emptyList(),
            showTransliteration = false,
        )

    private val content =
        PageContent(
            page = 1,
            items =
                listOf(
                    PageItem.Ayah(
                        AyahBlock(
                            ayah,
                            listOf(firstWord, secondWord),
                            transliteration = null,
                            translations = emptyList(),
                        ),
                    ),
                ),
        )

    private fun environment(
        settings: ReadSettings = wordByWord,
        wordStates: Map<WordKey, WordState> = emptyMap(),
        hintLetters: Map<WordKey, String> = emptyMap(),
        isReciting: Boolean = false,
        onWordTap: (AyahRef, AyahWord, Rect?) -> Unit = { _, _, _ -> },
    ) =
        ListPageEnvironment(
            settings = settings,
            arabicFamily = null,
            fontCache = PageFontCache(RecordingPackRepository()),
            wordStates = wordStates,
            hintLetters = hintLetters,
            isReciting = isReciting,
            selection = null,
            playingAyah = null,
            playingWordPosition = mutableStateOf(null),
            isFollowing = false,
            followRequest = 0,
            isPanelOpen = false,
            savedAyahs = emptySet(),
            contentPadding = PaddingValues(),
            onTap = {},
            onAyahLongPress = { _, _ -> },
            onWordTap = onWordTap,
            onUserScrolled = {},
        )

    private fun show(environment: ListPageEnvironment) {
        composeRule.setContent {
            AwnTheme { PageListView(content, isCurrent = true, environment = environment) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun eachWordShowsItsMeaningUnderIt() {
        show(environment())

        composeRule.onNodeWithText("All praise", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("(is) for Allah", substring = true).assertIsDisplayed()
    }

    @Test
    fun aTapOnAWordHandsTheScreenThatWordAndItsAyah() {
        var tappedAyah: AyahRef? = null
        var tappedWord: AyahWord? = null
        show(
            environment(
                onWordTap = { tapAyah, word, _ ->
                    tappedAyah = tapAyah
                    tappedWord = word
                },
            ),
        )

        composeRule
            .onNodeWithText("(is) for Allah", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(ayah, tappedAyah)
        assertNotNull(tappedWord)
        assertEquals(secondWord.key, tappedWord?.key)
    }

    @Test
    fun aHiddenWordShowsOnlyItsHintLetter() {
        show(
            environment(
                wordStates = mapOf(firstWord.key to WordState.Hidden),
                hintLetters = mapOf(firstWord.key to "ٱ"),
                isReciting = true,
            ),
        )

        composeRule.onNodeWithText("ٱ").assertIsDisplayed()
    }

    @Test
    fun withoutWordByWordTheMeaningsAreNotShown() {
        show(environment(settings = wordByWord.copy(showWordByWord = false), isReciting = true))

        composeRule.onNodeWithText("All praise", substring = true).assertDoesNotExist()
    }
}
