package app.awn.quran.feature.hifz

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.PageTableLoader
import app.awn.quran.feature.hifz.method.PartKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TIMEOUT_MILLIS = 10_000L

/**
 * The user could not tell what Finish part did and lost a lesson saved after 4 of 7 ayahs
 * (2026-10-24). End part opens a sheet with one answer per ayah; Save keeps the ayahs memorised in
 * a row and leaves the rest on Today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class EndPartFlowTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var database: HifzDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var hifz: HifzController

    private val text =
        FakeQuranTextRepository(
            wordsByAyah =
                (1..4)
                    .associate { number ->
                        ayah(number) to listOf(Word(WordKey(2, number, 1), "كلمة", null, null))
                    }
                    .toMutableMap(),
            pageByAyah = (1..4).associate { ayah(it) to 1 }.toMutableMap(),
            totalPages = 1,
        )

    @Before
    fun open() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    HifzDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        hifz =
            HifzController(
                HifzRepository(database),
                PageTableLoader(text),
                flowOf(ScriptId.QpcHafs),
                scope,
            )
        scope.launch { hifz.screenState.collect {} }
    }

    @After
    fun close() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        database.close()
    }

    private fun readyState(): HifzState {
        composeRule.waitUntil(TIMEOUT_MILLIS) { hifz.screenState.value is HifzScreenState.Ready }
        return (hifz.screenState.value as HifzScreenState.Ready).state
    }

    @Test
    fun savingTwoOfFourAyahsKeepsTheLessonOnTodayFromTheThird() {
        hifz.chooseMethod(HifzMethodId.SabaqSabqiManzil)
        composeRule.waitUntil(TIMEOUT_MILLIS) { hifz.startNextPart() != null }
        val ayahs = (1..4).map(::ayah)

        hifz.saveLesson(
            ayahs.associateWith { if (it <= ayah(2)) AyahFirmness.Firm else AyahFirmness.NotYet },
        )

        assertNull(hifz.practice.value)
        // The log and the plan's cursor reach the screen state through separate queries.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            val lesson = readyState().assignment.newLesson
            lesson?.isContinuation == true && lesson.part.first == ayah(3)
        }
        val lesson = checkNotNull(readyState().assignment.newLesson)
        assertFalse(lesson.isDone)
        assertEquals(ayah(3), lesson.part.first)
        assertEquals(PartKind.NewLesson, readyState().assignment.nextPart())
    }

    @Test
    fun theSheetShowsEveryAyahAndSaveSendsTheEditedAnswers() {
        val ayahs = (1..4).map(::ayah)
        var saved: EndPartAnswers? = null
        var keptPractising = false
        composeRule.setContent {
            AwnTheme {
                var answers by remember {
                    mutableStateOf<EndPartAnswers>(
                        EndPartAnswers.forLesson(ayahs, emptyMap(), emptyMap()),
                    )
                }
                EndPartSheet(
                    answers = answers,
                    context = "Sabaq · 2:1-4",
                    ayahText = { null },
                    onChange = { answers = it },
                    onSave = { saved = answers },
                    onKeepPractising = { keptPractising = true },
                )
            }
        }
        composeRule.onNodeWithText("4 of 4 ayahs memorised").assertExists()

        composeRule.onNodeWithTag("${END_PART_CHOICE_TAG_PREFIX}2-3-not_yet").performClick()
        composeRule.onNodeWithTag("${END_PART_CHOICE_TAG_PREFIX}2-3-not_yet").assertIsSelected()
        composeRule.onNodeWithText("Next time starts at 2:3.").assertExists()
        composeRule.onNodeWithTag(END_PART_SAVE_TAG).performClick()

        val lesson = saved as EndPartAnswers.Lesson
        assertEquals(AyahFirmness.NotYet, lesson.answers[ayah(3)])
        assertEquals(3, lesson.memorisedCount)
        assertFalse(keptPractising)
    }

    @Test
    fun saveStaysOnScreenUnderALongLesson() {
        val ayahs = (1..12).map(::ayah)
        composeRule.setContent {
            AwnTheme {
                EndPartSheet(
                    answers = EndPartAnswers.forLesson(ayahs, emptyMap(), emptyMap()),
                    context = "Sabaq · 2:1-12",
                    ayahText = { null },
                    onChange = {},
                    onSave = {},
                    onKeepPractising = {},
                )
            }
        }

        composeRule.onNodeWithTag(END_PART_SAVE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(END_PART_KEEP_TAG).assertIsDisplayed()
    }

    @Test
    fun keepPractisingClosesTheSheetWithoutSaving() {
        var saved = false
        var keptPractising = false
        composeRule.setContent {
            AwnTheme {
                EndPartSheet(
                    answers = EndPartAnswers.forLesson(listOf(ayah(1)), emptyMap(), emptyMap()),
                    context = "Sabaq · 2:1",
                    ayahText = { null },
                    onChange = {},
                    onSave = { saved = true },
                    onKeepPractising = { keptPractising = true },
                )
            }
        }

        composeRule.onNodeWithTag(END_PART_KEEP_TAG).performClick()

        assertTrue(keptPractising)
        assertFalse(saved)
    }
}
