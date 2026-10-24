package app.awn.quran.feature.hifz

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.data.toEntity
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.LessonPlanner
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.hifz.method.PageLayoutFamily
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTableLoader
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.method.RevisionSpacing
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HifzRepositoryTest {

    private lateinit var database: HifzDatabase
    private lateinit var repository: HifzRepository
    private val nowMillis =
        testDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000L

    @Before
    fun open() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    HifzDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        repository = HifzRepository(database, clock = { nowMillis }, zone = { ZoneOffset.UTC })
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun aPlanSurvivesASaveAndKeepsItsSettings() = runBlocking {
        val plan = planOf(cursor = ayah(5))

        repository.savePlan(plan)

        assertEquals(plan, repository.plan())
        assertEquals(plan, repository.observePlan().first())
    }

    @Test
    fun aPlanKeepsItsFixedStepsAndAnOlderRowKeepsItsMethodsSpacing() = runBlocking {
        val fixed =
            planOf(
                settings =
                    MethodSettings(revisionSpacing = RevisionSpacing.FixedSteps(listOf(2, 9, 40))),
            )

        repository.savePlan(fixed)

        assertEquals(fixed, repository.plan())
        // A row saved before the setting existed reads as the method's own spacing.
        database
            .dao()
            .upsertPlan(fixed.toEntity().copy(revisionSpacing = null, spacingSteps = null))
        assertEquals(RevisionSpacing.ManzilCycle, repository.plan()?.settings?.revisionSpacing)
    }

    @Test
    fun markingAgainKeepsAPagesHistoryAndASpreadMarkingSpacesFirstRevisions() = runBlocking {
        repository.markPages(1..30, PageStatus.Memorised)
        val firstDue = repository.records().values.map { it.card!!.dueMillis }.toSet()
        assertEquals(30, firstDue.size)

        repository.finishRevision(PartKind.Old, listOf(1), mapOf(1 to PartGrade.Good), null)
        val revised = repository.records().getValue(1)
        repository.markPages(1..1, PageStatus.Memorised)

        assertEquals(revised, repository.records().getValue(1))
    }

    @Test
    fun markingPagesWritesThemAndNotStartedRemovesThem() = runBlocking {
        repository.markPages(1..3, PageStatus.Solid)
        assertEquals(setOf(1, 2, 3), repository.records().keys)

        repository.markPages(2..2, PageStatus.NotStarted)

        assertEquals(setOf(1, 3), repository.records().keys)
        assertEquals(PageStatus.Solid, repository.records().getValue(1).status)
    }

    @Test
    fun finishingALessonRecordsThePagesTheCursorAndTheDay() = runBlocking {
        repository.savePlan(planOf())
        val table = smallTable()
        val part =
            LessonPlanner.nextPart(table, LessonSize.OnePage, null) { PageStatus.NotStarted }!!

        val answers =
            table.allAyahs
                .filter { it in part.first..part.last }
                .associateWith { AyahFirmness.Firm }
        repository.finishLesson(table, part, answers, durationMillis = 600_000L)

        assertEquals(PageStatus.Memorised, repository.records().getValue(1).status)
        assertEquals(ayah(5), repository.plan()?.cursor)
        val finished = repository.observeFinishedOn(testDay).first().single()
        assertEquals(PartKind.NewLesson, finished.kind)
        assertEquals(listOf(1), finished.pages)
        assertEquals(ayah(1), finished.firstAyah)
        assertEquals(ayah(4), finished.lastAyah)
        assertEquals(PartGrade.Good, finished.grade)
    }

    @Test
    fun aLessonSavedPartWayIsKeptOpenAndStartsAtItsFirstNotYetAyah() = runBlocking {
        repository.savePlan(planOf())
        val table = smallTable()
        val part = LessonPlanner.partBetween(table, ayah(1), ayah(7))
        val answers =
            table.allAyahs
                .filter { it in part.first..part.last }
                .associateWith { if (it <= ayah(4)) AyahFirmness.Firm else AyahFirmness.NotYet }

        repository.finishLesson(table, part, answers, durationMillis = null)

        assertEquals(ayah(5), repository.plan()?.cursor)
        val finished = repository.observeFinishedOn(testDay).first().single()
        assertFalse(finished.isComplete)
    }

    @Test
    fun aRevisionSavedPartWayGradesOnlyThePagesReached() = runBlocking {
        repository.savePlan(planOf())
        repository.markPages(1..3, PageStatus.Memorised)

        repository.finishRevision(PartKind.Old, listOf(1, 2, 3), mapOf(1 to PartGrade.Good), null)

        assertEquals(testDay, repository.records().getValue(1).lastRevisedOn)
        assertNull(repository.records().getValue(2).lastRevisedOn)
        val finished = repository.observeFinishedOn(testDay).first().single()
        assertEquals(listOf(1, 2, 3), finished.pages)
        assertEquals(listOf(1), finished.gradedPages)
        assertFalse(finished.isComplete)

        repository.finishRevision(
            PartKind.Old,
            listOf(1, 2, 3),
            mapOf(2 to PartGrade.Hard, 3 to PartGrade.Good),
            null,
        )

        assertTrue(repository.observeFinishedOn(testDay).first().last().isComplete)
    }

    @Test
    fun finishingARevisionGradesEveryPageOfTheGroup() = runBlocking {
        repository.savePlan(planOf())
        repository.markPages(1..2, PageStatus.Memorised)

        repository.finishRevision(
            PartKind.Old,
            listOf(1, 2),
            mapOf(1 to PartGrade.Forgot, 2 to PartGrade.Forgot),
            null,
        )

        assertEquals(PageStatus.Learning, repository.records().getValue(1).status)
        assertEquals(PageStatus.Learning, repository.records().getValue(2).status)
        assertEquals(listOf(1, 2), repository.observeFinishedOn(testDay).first().single().pages)
        assertNull(repository.observeFinishedOn(testDay.minusDays(1)).first().firstOrNull())
    }

    @Test
    fun mistakesComeBackAsRecords() = runBlocking {
        val mistake = MistakeRecord(ayah(155), MistakeType.Skipped, nowMillis)

        repository.recordMistakes(listOf(mistake))

        assertEquals(listOf(mistake), repository.observeMistakesSince(0).first())
    }

    @Test
    fun theLoaderReadsEveryPageOfThePrintOnce() = runBlocking {
        val text =
            FakeQuranTextRepository(
                pageByAyah = mutableMapOf(ayah(1) to 1, ayah(2) to 1, ayah(3) to 2),
                totalPages = 2,
            )
        val loader = PageTableLoader(text)

        val table = loader.load(PageLayoutFamily.Madani1421)

        assertEquals(2, table.pageCount)
        assertEquals(listOf(ayah(3)), table.ayahsOn(2))
        assertEquals(PageLayoutFamily.Madani1405, PageLayoutFamily.of(ScriptId.V1))
        assertEquals(PageLayoutFamily.IndoPak15Lines, PageLayoutFamily.of(ScriptId.IndoPak15))
        assertEquals(
            ScriptId.IndoPak15,
            PageLayoutFamily.scriptFor(PageLayoutFamily.IndoPak15Lines),
        )
    }
}
