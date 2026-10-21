package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.PageFontCache
import app.awn.quran.feature.read.quran.AyahBlock
import app.awn.quran.feature.read.quran.AyahTranslation
import app.awn.quran.feature.read.quran.AyahWord
import app.awn.quran.feature.read.quran.ListPageEnvironment
import app.awn.quran.feature.read.quran.PageContent
import app.awn.quran.feature.read.quran.PageItem
import app.awn.quran.feature.read.quran.PageListView
import app.awn.quran.feature.read.quran.listContentPadding
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The line-by-line list scrolls to the ayah being recited. Before this, a page with several
 * translations showed one or two ayahs, the reciter moved on below the screen, and the fills there
 * were never seen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListRecitationFollowTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val fontCache by lazy { PageFontCache(RecordingPackRepository()) }

    private val settings =
        ReadSettings(
            script = ScriptId.QpcHafs,
            pageView = PageView.LineByLine,
            showWordByWord = false,
            showTransliteration = false,
        )

    private fun block(ayahNumber: Int): AyahBlock {
        val ayah = AyahRef(2, ayahNumber)
        return AyahBlock(
            ayah = ayah,
            words =
                listOf(
                    AyahWord(
                        key = WordKey(2, ayahNumber, 1),
                        arabic = "word$ayahNumber",
                        meaning = null,
                        transliteration = null,
                    ),
                ),
            transliteration = null,
            // Long enough that only a few ayahs fit on the screen.
            translations =
                listOf(AyahTranslation("T", null, "translation $ayahNumber ".repeat(LINES))),
        )
    }

    private val content =
        PageContent(page = 3, items = (1..AYAH_COUNT).map { PageItem.Ayah(block(it)) })

    private fun environment(
        recitedAyah: AyahRef?,
        contentPadding: PaddingValues = PaddingValues(),
    ) =
        ListPageEnvironment(
            settings = settings,
            arabicFamily = null,
            fontCache = fontCache,
            wordStates = mapOf(WordKey(2, 1, 1) to WordState.Confirmed),
            isReciting = true,
            recitedAyah = recitedAyah,
            selection = null,
            playingAyah = null,
            playingWordPosition = mutableStateOf(null),
            isFollowing = false,
            followRequest = 0,
            isPanelOpen = false,
            savedAyahs = emptySet(),
            contentPadding = contentPadding,
            onTap = {},
            onAyahLongPress = { _, _ -> },
            onWordTap = { _, _, _ -> },
            onUserScrolled = {},
        )

    @Test
    fun theListScrollsToTheAyahBeingRecited() {
        val recited = mutableStateOf(AyahRef(2, 1))
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.height(SCREEN_HEIGHT.dp)) {
                    PageListView(content, isCurrent = true, environment(recited.value))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("word1").assertIsDisplayed()
        composeRule.onNodeWithText("word9").assertDoesNotExist()

        recited.value = AyahRef(2, 9)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("word9").assertIsDisplayed()
    }

    @Test
    fun theLastAyahCanReachTheTopOfTheListWhileReciting() {
        val recited = mutableStateOf(AyahRef(2, 1))
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.height(SCREEN_HEIGHT.dp)) {
                    PageListView(content, isCurrent = true, environment(recited.value))
                }
            }
        }
        composeRule.waitForIdle()

        recited.value = AyahRef(2, AYAH_COUNT)
        composeRule.waitForIdle()

        // Without tail space the list stops at its end and the last ayah stays at the bottom, under
        // the recite bar.
        val top = composeRule.onNodeWithText("word$AYAH_COUNT").getUnclippedBoundsInRoot().top
        assertTrue("last ayah top was $top", top < (SCREEN_HEIGHT * UPPER_SHARE).dp)
    }

    @Test
    fun everyRecitedAyahEndsAboveTheReciteBar() {
        val recited = mutableStateOf(AyahRef(2, 1))
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.height(SCREEN_HEIGHT.dp)) {
                    PageListView(
                        content,
                        isCurrent = true,
                        environment(
                            recited.value,
                            contentPadding =
                                listContentPadding(BAR_HEIGHT.dp, navigationBarHeight = 0.dp),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        for (ayahNumber in 1..AYAH_COUNT) {
            recited.value = AyahRef(2, ayahNumber)
            composeRule.waitForIdle()
            val word = composeRule.onNodeWithText("word$ayahNumber").getUnclippedBoundsInRoot()
            assertTrue(
                "ayah $ayahNumber ends at ${word.bottom}, under the bar",
                word.bottom <= (SCREEN_HEIGHT - BAR_HEIGHT).dp,
            )
        }
    }

    private companion object {
        const val BAR_HEIGHT = 200
        const val UPPER_SHARE = 0.4f
        const val AYAH_COUNT = 12
        const val LINES = 20
        const val SCREEN_HEIGHT = 600
    }
}
