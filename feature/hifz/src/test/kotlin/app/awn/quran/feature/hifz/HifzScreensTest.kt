package app.awn.quran.feature.hifz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTable
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.ui.HifzActions
import app.awn.quran.feature.hifz.ui.HifzMainContent
import app.awn.quran.feature.hifz.ui.MarkKnownScreen
import app.awn.quran.feature.hifz.ui.MethodChooser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Screens are drawn 360 dp wide at font scale 2.0, the narrowest and largest case of the design
// system, in every colour mode.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HifzScreensTest {

    @get:Rule val composeRule = createComposeRule()

    private val table = smallTable()
    private val fatihahOnly = PageTable(listOf(listOf(AyahRef(1, 1), AyahRef(1, 2))), listOf(1))

    private fun stateOf(
        method: HifzMethodId = HifzMethodId.SabaqSabqiManzil,
        finished: List<FinishedPart> = emptyList(),
    ): HifzState {
        val records =
            mapOf(
                1 to known(1, learnedDaysAgo = 3),
                2 to known(2, learnedDaysAgo = 20, revisedDaysAgo = 9),
                3 to PageStatus.Solid.let { known(3).copy(status = it) },
            )
        val mistakes =
            listOf(
                MistakeRecord(ayah(155), MistakeType.Skipped, 1),
                MistakeRecord(ayah(155), MistakeType.Skipped, 2),
            )
        return HifzStateBuilder.build(
            planOf(method = method, settings = MethodSettings(revisionPageLimit = 10)),
            table,
            records,
            finished,
            mistakes,
            testDay,
        )
    }

    private fun show(
        mode: ColourMode,
        isScrolled: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalColourMode provides mode,
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                AwnTheme {
                    val scrolling =
                        if (isScrolled) Modifier.verticalScroll(rememberScrollState()) else Modifier
                    Box(Modifier.width(360.dp).then(scrolling)) { content() }
                }
            }
        }
    }

    private val noActions =
        HifzActions(onOpenMethod = {}, onStartPart = {}, onOpenMark = {}, onOpenWeakSpot = {})

    private fun assertMainPageDraws(mode: ColourMode) {
        show(mode) { HifzMainContent(stateOf(), { "Al-Baqarah" }, noActions) }

        composeRule.onNodeWithText("Method").assertIsDisplayed()
        composeRule.onNodeWithText("Sabaq").assertExists()
        composeRule.onNodeWithText("Start Sabaq").assertExists()
        composeRule.onNodeWithText("Progress map").assertExists()
        composeRule.onNodeWithText("Mark what I know").assertExists()
        composeRule.onNodeWithText("Weak spots").assertExists()
        composeRule.onNodeWithText("Al-Baqarah 2:155").assertExists()
    }

    @Test fun theMainPageDrawsInAwnMode() = assertMainPageDraws(ColourMode.Awn)

    @Test fun theMainPageDrawsInLightMode() = assertMainPageDraws(ColourMode.Light)

    @Test fun theMainPageDrawsInDarkMode() = assertMainPageDraws(ColourMode.Dark)

    @Test fun theMainPageDrawsInBlackMode() = assertMainPageDraws(ColourMode.Black)

    @Test
    fun theStartButtonAsksForTheNextPartAndRowsAskForTheirOwn() {
        val asked = mutableListOf<PartKind>()
        show(ColourMode.Awn) {
            HifzMainContent(
                stateOf(),
                { "Al-Baqarah" },
                noActions.copy(onStartPart = { asked += it }),
            )
        }

        composeRule.onNodeWithText("Start Sabaq").performScrollTo().performClick()
        composeRule.onNodeWithText("Sabqi").performScrollTo().performClick()

        assertEquals(listOf(PartKind.NewLesson, PartKind.Recent), asked)
    }

    @Test
    fun aFinishedLessonShowsDoneAndTheButtonMovesOn() {
        val done =
            FinishedPart(
                testDay,
                PartKind.NewLesson,
                listOf(3, 4),
                ayah(9),
                ayah(12),
                PartGrade.Good,
            )
        show(ColourMode.Awn) {
            HifzMainContent(stateOf(finished = listOf(done)), { "" }, noActions)
        }

        composeRule.onNodeWithText("Done").assertExists()
        composeRule.onNodeWithText("Start Sabqi").assertExists()
    }

    @Test
    fun trackOnlyShowsNoTodaySection() {
        show(ColourMode.Awn) {
            HifzMainContent(stateOf(HifzMethodId.TrackOnly), { "" }, noActions)
        }

        composeRule.onNodeWithText("Today").assertDoesNotExist()
        composeRule.onNodeWithText("Progress map").assertExists()
    }

    @Test
    fun theMethodListHasFiveChoicesAndUsesTheTickedOne() {
        var chosen: Pair<HifzMethodId, MethodSettings>? = null
        show(ColourMode.Dark) {
            MethodChooser(
                initialMethod = HifzMethodId.SabaqSabqiManzil,
                initialSettings = MethodSettings(),
                onUse = { method, settings -> chosen = method to settings },
            )
        }

        composeRule.onNodeWithText("Choose a method").assertExists()
        composeRule.onNodeWithText("3 by 3").assertExists()
        composeRule.onNodeWithText("No method, just track").assertExists()
        composeRule.onNodeWithText("Old portion again after").assertExists()
        composeRule.onNodeWithText("3 by 3").performScrollTo().performClick()
        composeRule.onNodeWithText("Use this method").performScrollTo().performClick()

        assertEquals(HifzMethodId.ThreeByThree, chosen?.first)
    }

    @Test
    fun markingASurahSavesItsPagesWithTheChosenState() {
        var saved: Pair<IntRange, PageStatus>? = null
        show(ColourMode.Black, isScrolled = false) {
            MarkKnownScreen(
                table = fatihahOnly,
                surahName = { "Al-Fatihah" },
                onSave = { range, status -> saved = range to status },
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Save").performClick()

        assertEquals(1..1, saved?.first)
        assertEquals(PageStatus.Memorised, saved?.second)
    }

    @Test
    fun theTodayLineNamesTheLessonPageAndTheRevisionLeft() {
        val summary = HifzToday.summaryOf(stateOf())

        assertNotNull(summary)
        assertEquals(4, summary?.lessonPage)
        assertEquals(true, summary?.isSabaq)
        assertNull(HifzToday.summaryOf(stateOf(HifzMethodId.TrackOnly)))
    }
}
