package app.awn.quran.index

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.tabs.AwnTab
import app.awn.quran.tabs.AwnTabBar
import app.awn.quran.tabs.TodayItem
import org.junit.Assert.assertEquals
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
                    firstLineWords = listOf("كلمة", "أخرى"),
                    isStart = false,
                ),
            lastPage = 294,
            lastSurah = 18,
        )

    private var openedPage: Int? = null
    private var openedToday = false
    private var openedDuas = 0

    private fun show(todayItems: List<TodayItem>, duaCount: Int? = null) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                QuranTabScreen(
                    state = state,
                    todayItems = todayItems,
                    duaCount = duaCount,
                    actions =
                        QuranTabActions(
                            onSearch = {},
                            onBookmarks = {},
                            onSettings = {},
                            onDuas = { openedDuas++ },
                            onOpen = { page, _: AyahRef? -> openedPage = page },
                        ),
                )
            }
        }
    }

    @Test
    fun theContinueCardNamesTheSurahPageAndJuzAndOpensThePage() {
        show(emptyList())

        composeRule.onNodeWithText("Continue reading").assertIsDisplayed()
        composeRule.onNodeWithText("Surah 18 · page 294 · Juz 15").performClick()

        assertEquals(294, openedPage)
    }

    @Test
    fun withNoTodayRowsTheSectionIsAbsent() {
        show(emptyList())

        composeRule.onNodeWithText("Today").assertDoesNotExist()
    }

    @Test
    fun aTodayRowShowsItsTitleAndDetailAndOpens() {
        show(
            listOf(
                TodayItem(
                    "Learn",
                    "Revise 5 ayahs",
                    icon = R.drawable.ic_app_school,
                    onOpen = { openedToday = true },
                ),
            ),
        )

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Revise 5 ayahs").performClick()

        assertEquals(true, openedToday)
    }

    @Test
    fun beforeTheCollectionIsReadThereIsNoDuasEntry() {
        show(emptyList(), duaCount = null)

        composeRule.onNodeWithText("Browse").assertDoesNotExist()
        composeRule.onNodeWithText("Duas from the Quran").assertDoesNotExist()
    }

    @Test
    fun theDuasEntryShowsItsNameAndCountAndOpens() {
        show(emptyList(), duaCount = 77)

        composeRule.onNodeWithText("Browse").assertIsDisplayed()
        composeRule.onNodeWithText("Duas from the Quran").assertIsDisplayed()
        composeRule.onNodeWithText("77 duas").performClick()

        assertEquals(1, openedDuas)
    }

    @Test
    fun theDuasEntrySitsAboveTheSurahList() {
        show(emptyList(), duaCount = 77)

        composeRule.onNodeWithText("Duas from the Quran").assertIsDisplayed()
        composeRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun theShowChoiceSwitchesTheListToJuz() {
        show(emptyList())

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
