package app.awn.quran.index

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.tabs.AwnTab
import app.awn.quran.tabs.AwnTabBar
import app.awn.quran.tabs.TodayItem
import app.awn.quran.tabs.TodayTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Renders the Quran tab and the tab bar in Awn and Black modes at font scales 1.0 and 1.3. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp", application = Application::class)
class QuranTabRenderTest(private val mode: ColourMode, private val fontScale: Float) {

    @get:Rule val composeRule = createComposeRule()

    init {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application.packageManager)
            .addActivityIfNotPresent(
                ComponentName(application.packageName, "androidx.activity.ComponentActivity"),
            )
    }

    private val chapters =
        (1..114).map { number ->
            Chapter(
                number = number,
                nameArabic = "س$number",
                nameSimple = "Surah $number",
                nameBangla = null,
                versesCount = 7,
                revelationPlace = "makkah",
                bismillahPre = true,
            )
        }
    private val surahPages = (1..114).associateWith { it * 5 - 4 }
    private val juzPages = (1..30).associateWith { it * 20 - 19 }

    private val content =
        IndexContent(
            script = ScriptId.V2,
            chapters = chapters,
            surahRows = buildSurahRows(chapters, surahPages, juzPages),
            juzRows = buildJuzRows(chapters, juzPages),
            pageCount = 604,
            juzStartPages = juzPages,
        )

    private val state =
        IndexState(
            content = content,
            continueReading =
                ContinueReading(
                    page = 294,
                    chapter = chapters[17],
                    juz = 15,
                    firstWord = WordKey(18, 5, 1),
                    isStart = false,
                ),
            lastPage = 294,
            lastSurah = 18,
        )

    private var openedPage: Int? = null
    private var openedHifz = 0
    private var openedLearn = 0
    private var openedDuas = 0
    private var openedTopics = 0

    private val tiles =
        TodayTiles(
            hifz = TodayItem("Hifz", "Sabaq p. 1", R.drawable.ic_app_psychology) { openedHifz++ },
            learn = TodayItem("Words", "10 min", R.drawable.ic_app_school) { openedLearn++ },
        )

    private fun show(duaCount: Int? = 89, topicCount: Int? = 32) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                QuranTabScreen(
                    state = state,
                    todayTiles = tiles,
                    duaCount = duaCount,
                    topicCount = topicCount,
                    actions =
                        QuranTabActions(
                            onSearch = {},
                            onBookmarks = {},
                            onSettings = {},
                            onDuas = { openedDuas++ },
                            onTopics = { openedTopics++ },
                            onOpen = { page, _: AyahRef? -> openedPage = page },
                        ),
                )
            }
        }
    }

    @Test
    fun theContinueCardNamesTheSurahAndPageAndOpensThePage() {
        show()

        composeRule.onNodeWithText("Continue reading").assertIsDisplayed()
        composeRule.onNodeWithText("Surah 18 · page 294").performClick()

        assertEquals(294, openedPage)
    }

    @Test
    fun fourTilesShowTheirDetailAndOpenTheirDestinations() {
        show()

        composeRule.onNodeWithText("Sabaq p. 1").performClick()
        composeRule.onNodeWithText("10 min").performClick()
        composeRule.onNodeWithText("89 duas").performClick()
        composeRule.onNodeWithText("32 topics").performClick()

        assertEquals(listOf(1, 1, 1, 1), listOf(openedHifz, openedLearn, openedDuas, openedTopics))
    }

    @Test
    fun theTilesHaveNoSectionHeadings() {
        show()

        composeRule.onNodeWithText("Today").assertDoesNotExist()
        composeRule.onNodeWithText("Browse").assertDoesNotExist()
    }

    @Test
    fun beforeTheCollectionsAreReadTheFourTilesStillShow() {
        show(duaCount = null, topicCount = null)

        composeRule.onNodeWithTag(HIFZ_TILE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LEARN_TILE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSE_DUAS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSE_TOPICS_TAG).assertIsDisplayed()
    }

    @Test
    fun theFourTilesSitInOneRowOfEqualWidthAboveTheSurahList() {
        show()

        val bounds =
            listOf(HIFZ_TILE_TAG, LEARN_TILE_TAG, BROWSE_DUAS_TAG, BROWSE_TOPICS_TAG).map {
                composeRule.onNodeWithTag(it).getBoundsInRoot()
            }
        assertEquals(1, bounds.map { it.top }.distinct().size)
        assertEquals(1, bounds.map { it.width }.distinct().size)
        composeRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun theDailySnippetIsNotOnTheQuranTab() {
        show()

        composeRule.onNodeWithText("Show another").assertDoesNotExist()
    }

    @Test
    fun theListHeaderStaysOnScreenWhileTheListScrolls() {
        show()

        composeRule.onNodeWithText("Go to page").assertIsDisplayed()
        composeRule.onNodeWithTag(SURAH_LIST_TAG).performScrollToIndex(60)

        composeRule.onNodeWithText("Go to page").assertIsDisplayed()
        composeRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun theJuzRailStartsUnderThePinnedHeaderAfterScrolling() {
        show()

        composeRule.onNodeWithTag(SURAH_LIST_TAG).performScrollToIndex(60)

        val headerBottom = composeRule.onNodeWithText("Go to page").getBoundsInRoot().bottom
        val railTop = composeRule.onNodeWithContentDescription("Jump to juz").getBoundsInRoot().top
        assertTrue("rail $railTop under header $headerBottom", railTop >= headerBottom)
    }

    @Test
    fun theShowChoiceSwitchesTheListToJuz() {
        show()

        composeRule.onNodeWithText("Show").assertIsDisplayed()
        composeRule.onNodeWithText("Surahs").performClick()
        composeRule.onNodeWithText("Juz").performClick()

        composeRule.onNodeWithText("Surah 1 1:1").assertIsDisplayed()
    }

    @Test
    fun theTabBarShowsThreeDestinations() {
        composeRule.setContent {
            CompositionLocalProvider(LocalColourMode provides mode) {
                AwnTabBar(selected = AwnTab.Learn, onSelect = {})
            }
        }

        composeRule.onNodeWithText("Quran").assertIsDisplayed()
        composeRule.onNodeWithText("Learn").assertIsDisplayed()
        composeRule.onNodeWithText("Hifz").assertIsDisplayed()
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0} at font scale {1}")
        fun parameters(): List<Array<Any>> =
            listOf(
                arrayOf(ColourMode.Awn, 1.0f),
                arrayOf(ColourMode.Awn, 1.3f),
                arrayOf(ColourMode.Black, 1.0f),
                arrayOf(ColourMode.Black, 1.3f),
            )
    }
}
