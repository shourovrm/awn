package app.awn.quran.feature.hifz

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.LessonPlanner
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.PageStatus
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A user on "fixed ayahs per day" had no way to say how far they have already memorised, so the
 * plan began at the first ayah of the Quran. "I know up to" an ayah moves the plan's next lesson to
 * the ayah after it and marks every page that lies wholly before that point as memorised; the page
 * it ends on stays open because part of it is still to learn.
 */
@RunWith(RobolectricTestRunner::class)
class KnownUpToTest {

    private lateinit var database: HifzDatabase
    private lateinit var repository: HifzRepository
    private val nowMillis =
        testDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000L
    private val table = smallTable()

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

    private val fixedAyahs =
        planOf(
            method = HifzMethodId.FixedAyahs,
            settings = MethodSettings(ayahsPerDay = 3),
        )

    @Test
    fun theNextLessonStartsAfterTheLastKnownAyah() = runBlocking {
        repository.savePlan(fixedAyahs)

        repository.setKnownUpTo(table, ayah(10))

        assertEquals(ayah(11), repository.plan()?.cursor)
        val records = repository.records()
        val part =
            LessonPlanner.nextPart(table, LessonSize.Ayahs(3), repository.plan()?.cursor) {
                records[it]?.status ?: PageStatus.NotStarted
            }
        assertEquals(ayah(11), part?.first)
        assertEquals(ayah(13), part?.last)
    }

    @Test
    fun pagesWhollyBeforeThePointAreMarkedMemorised() = runBlocking {
        repository.savePlan(fixedAyahs)

        repository.setKnownUpTo(table, ayah(10))

        val records = repository.records()
        assertEquals(PageStatus.Memorised, records[1]?.status)
        assertEquals(PageStatus.Memorised, records[2]?.status)
        // Page 3 holds ayahs 9 to 12; 11 and 12 are still to learn.
        assertFalse(records[3]?.status?.isKnown ?: false)
    }

    @Test
    fun knowingTheLastAyahOfAPageMarksThatPageToo() = runBlocking {
        repository.savePlan(fixedAyahs)

        repository.setKnownUpTo(table, ayah(8))

        assertTrue(repository.records()[2]?.status?.isKnown ?: false)
        assertEquals(ayah(9), repository.plan()?.cursor)
    }

    @Test
    fun movingThePointBackLeavesPagesTheUserMarkedAlone() = runBlocking {
        repository.savePlan(fixedAyahs)
        repository.markPages(5..6, PageStatus.Solid)

        repository.setKnownUpTo(table, ayah(4))

        assertEquals(ayah(5), repository.plan()?.cursor)
        assertEquals(PageStatus.Solid, repository.records()[5]?.status)
    }

    @Test
    fun withoutAPlanNothingIsWritten() = runBlocking {
        repository.setKnownUpTo(table, ayah(10))

        assertEquals(null, repository.plan())
        assertTrue(repository.records().isEmpty())
    }
}
