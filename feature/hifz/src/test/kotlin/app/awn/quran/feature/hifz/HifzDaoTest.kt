package app.awn.quran.feature.hifz

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.MistakeEntity
import app.awn.quran.feature.hifz.data.MistakeKind
import app.awn.quran.feature.hifz.data.PageProgressEntity
import app.awn.quran.feature.hifz.data.PlanEntity
import app.awn.quran.feature.hifz.data.SessionEntity
import app.awn.quran.feature.hifz.data.SessionKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HifzDaoTest {

    private lateinit var database: HifzDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, HifzDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun plan(method: String) =
        PlanEntity(
            method = method,
            lessonKind = "page",
            lessonAyahs = 0,
            manzilCycleDays = 7,
            recentDays = 7,
            ayahsPerDay = 5,
            revisionPageLimit = 10,
            cursorSurah = null,
            cursorAyah = null,
            printLayout = "madani_1421",
            startedOn = 20_000,
        )

    private fun page(page: Int, status: Int) =
        PageProgressEntity(
            page = page,
            status = status,
            learnedOn = null,
            lastRevisedOn = null,
            stability = null,
            difficulty = null,
            due = null,
            lastReview = null,
            state = 0,
        )

    @Test
    fun theTableHoldsOnePlanAndASecondSaveReplacesIt() = runBlocking {
        val dao = database.dao()
        assertNull(dao.findPlan())

        dao.upsertPlan(plan("sabaq_sabqi_manzil"))
        dao.upsertPlan(plan("fixed_ayahs"))

        assertEquals("fixed_ayahs", dao.findPlan()?.method)
    }

    @Test
    fun pagesComeBackInPageOrderAndCanBeDeleted() = runBlocking {
        val dao = database.dao()
        dao.upsertPages(listOf(page(30, 2), page(5, 1), page(7, 3)))

        assertEquals(listOf(5, 7, 30), dao.allPages().map { it.page })

        dao.deletePages(listOf(7))
        assertEquals(listOf(5, 30), dao.allPages().map { it.page })

        dao.deleteAllPages()
        assertEquals(emptyList<PageProgressEntity>(), dao.allPages())
    }

    @Test
    fun sessionsAreReadBackPerDay() = runBlocking {
        val dao = database.dao()
        fun session(day: Long, kind: String) =
            SessionEntity(
                day = day,
                kind = kind,
                pages = "24",
                firstSurah = null,
                firstAyah = null,
                lastSurah = null,
                lastAyah = null,
                grade = "Good",
                at = day * 86_400_000L,
            )
        dao.insertSession(session(100, SessionKind.NEW_LESSON))
        dao.insertSession(session(100, SessionKind.RECENT))
        dao.insertSession(session(101, SessionKind.OLD))

        assertEquals(
            listOf(SessionKind.NEW_LESSON, SessionKind.RECENT),
            dao.sessionsOn(100).map { it.kind },
        )
    }

    @Test
    fun mistakesBeforeTheStartOfTheWindowAreLeftOut() = runBlocking {
        val dao = database.dao()
        dao.insertMistakes(
            listOf(
                MistakeEntity(surah = 2, ayah = 155, kind = MistakeKind.SKIPPED, at = 1_000),
                MistakeEntity(surah = 2, ayah = 155, kind = MistakeKind.SKIPPED, at = 5_000),
            ),
        )

        assertEquals(listOf(5_000L), dao.mistakesSince(2_000).map { it.at })
    }
}
