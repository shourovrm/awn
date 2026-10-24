package app.awn.quran.feature.hifz

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.JournalEntry
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.ui.JournalContent
import app.awn.quran.feature.hifz.ui.JournalScreen
import app.awn.quran.feature.hifz.ui.PageFacts
import app.awn.quran.feature.hifz.ui.PageSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class JournalScreensTest {

    @get:Rule val composeRule = createComposeRule()

    private val entry =
        JournalEntry(
            day = testDay,
            atMillis = 0,
            kind = PartKind.NewLesson,
            pages = listOf(1, 2),
            firstAyah = ayah(1),
            lastAyah = ayah(7),
            grade = PartGrade.Good,
            isComplete = false,
            durationMillis = 14 * 60_000L,
            ayahAnswers =
                (1..7).associate {
                    ayah(it) to if (it <= 4) AyahFirmness.Firm else AyahFirmness.NotYet
                },
            pageGrades = emptyMap(),
        )

    @Test
    fun anEntryShowsItsPartRangeResultAndTimeAndOpensItsFirstAyah() {
        var opened: AyahRef? = null
        composeRule.setContent {
            AwnTheme {
                JournalScreen(
                    content =
                        JournalContent(
                            listOf(entry),
                            emptyList(),
                            HifzMethodId.SabaqSabqiManzil,
                            testDay,
                        ),
                    surahName = { "Al-Baqarah" },
                    onOpenAyah = { opened = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Today").assertExists()
        composeRule.onNodeWithText("Sabaq · Al-Baqarah ayahs 1 to 7").assertExists()
        composeRule.onNodeWithText("4 of 7 ayahs memorised · 14 min").performClick()

        assertEquals(ayah(1), opened)
    }

    @Test
    fun anEmptyJournalSaysSo() {
        composeRule.setContent {
            AwnTheme {
                JournalScreen(
                    content =
                        JournalContent(
                            emptyList(),
                            emptyList(),
                            HifzMethodId.SabaqSabqiManzil,
                            testDay,
                        ),
                    surahName = { "" },
                    onOpenAyah = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Nothing saved in the last 60 days.").assertExists()
    }

    @Test
    fun thePageSheetShowsTheRecordAndChangesTheState() {
        var changedTo: PageStatus? = null
        val facts =
            PageFacts(
                page = 24,
                status = PageStatus.Learning,
                firstAyah = ayah(154),
                lastAyah = ayah(163),
                ayahCount = 10,
                memorisedAyahs = 4,
                learnedOn = testDay,
                lastRevisedOn = null,
                nextRevision = null,
                isInCycle = false,
                mistakesThisWeek = 3,
            )
        composeRule.setContent {
            AwnTheme {
                PageSheet(
                    facts = facts,
                    surahName = { "Al-Baqarah" },
                    onChangeState = { changedTo = it },
                    onOpenPage = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Page 24 · Learning").assertExists()
        composeRule.onNodeWithText("4 of 10 ayahs memorised").assertExists()
        composeRule.onNodeWithTag("hifz-page-state-memorised").performClick()

        assertEquals(PageStatus.Memorised, changedTo)
    }
}
