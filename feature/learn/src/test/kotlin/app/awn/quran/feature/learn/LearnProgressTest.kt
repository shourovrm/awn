package app.awn.quran.feature.learn

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.fsrs.FsrsState
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.progress.LearnClock
import app.awn.quran.feature.learn.progress.LearnProgress
import app.awn.quran.feature.learn.progress.LearnSettings
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearnProgressTest {

    private val zone = ZoneId.of("Asia/Dhaka")
    private var now: Instant = Instant.parse("2026-03-10T03:00:00Z")
    private lateinit var database: LearnDatabase
    private lateinit var settings: LearnSettings
    private lateinit var progress: LearnProgress
    private val ayah = AyahRef(1, 2)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, LearnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val file = File.createTempFile("learn_settings", ".preferences_pb").also { it.delete() }
        settings =
            LearnSettings(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                ) {
                    file
                },
            )
        progress = LearnProgress(database, settings, LearnClock(now = { now }, zone = { zone }))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun advanceDays(days: Long) {
        now = now.plusSeconds(days * SECONDS_PER_DAY)
    }

    @Test
    fun aCleanHifzCompletionCreatesAGoodCardDueInDays() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)

        val card = database.dao().findCard(1, 2)!!
        assertEquals(FsrsState.Review.value, card.state)
        assertEquals(1, card.reps)
        assertEquals(0, card.lapses)
        assertTrue(card.due - now.toEpochMilli() >= MILLIS_PER_DAY)
    }

    @Test
    fun aCompletionWithSkipsIsRatedAgain() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)
        val afterGood = database.dao().findCard(1, 2)!!
        advanceDays(5)

        progress.hifzCompleted(ayah, skips = 2)

        val afterSkips = database.dao().findCard(1, 2)!!
        assertEquals(1, afterSkips.lapses)
        assertEquals(2, afterSkips.reps)
        assertTrue(afterSkips.stability < afterGood.stability)
    }

    @Test
    fun oneSkipIsRatedHardAndTwoAreRatedAgain() = runBlocking {
        val hardAyah = AyahRef(1, 3)
        progress.hifzCompleted(ayah, skips = 0)
        progress.hifzCompleted(hardAyah, skips = 0)
        advanceDays(5)

        progress.hifzCompleted(hardAyah, skips = 1)

        val hardCard = database.dao().findCard(1, 3)!!
        assertEquals(0, hardCard.lapses)
        assertTrue(hardCard.reps == 2)
    }

    @Test
    fun gradingAfterAnAutomaticRatingReplacesIt() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)
        advanceDays(5)
        progress.hifzCompleted(ayah, skips = 0)
        val reference = database.dao().findCard(1, 2)!!

        progress.gradeAyah(ayah, FsrsRating.Again)

        val regraded = database.dao().findCard(1, 2)!!
        assertEquals(reference.reps, regraded.reps)
        assertEquals(1, regraded.lapses)
        assertTrue(regraded.stability < reference.stability)
    }

    @Test
    fun gradingAFirstTimeAutomaticRatingLeavesOneReview() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)

        progress.gradeAyah(ayah, FsrsRating.Easy)

        assertEquals(1, database.dao().findCard(1, 2)!!.reps)
    }

    @Test
    fun gradingWithoutAnAutomaticRatingIsAPlainRating() = runBlocking {
        progress.gradeAyah(ayah, FsrsRating.Hard)
        progress.gradeAyah(ayah, FsrsRating.Good)

        assertEquals(2, database.dao().findCard(1, 2)!!.reps)
    }

    @Test
    fun anAutomaticRatingOlderThanTenMinutesIsNotReplaced() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)
        now = now.plusSeconds(11 * 60)

        progress.gradeAyah(ayah, FsrsRating.Good)

        assertEquals(2, database.dao().findCard(1, 2)!!.reps)
    }

    @Test
    fun dueAyahsComeInReadingOrder() = runBlocking {
        progress.rateCard(AyahRef(2, 5), FsrsRating.Good)
        progress.rateCard(AyahRef(1, 7), FsrsRating.Good)
        progress.rateCard(AyahRef(1, 3), FsrsRating.Good)
        advanceDays(60)

        assertEquals(listOf(AyahRef(1, 3), AyahRef(1, 7), AyahRef(2, 5)), progress.dueAyahs())
    }

    @Test
    fun threeCleanCompletionsOnThreeDaysMemoriseTheAyah() = runBlocking {
        repeat(3) {
            progress.hifzCompleted(ayah, skips = 0)
            advanceDays(1)
        }

        val snapshot = progress.snapshot()

        assertEquals(setOf(ayah), snapshot.memorisedByAyah)
        assertEquals(mapOf(1 to 1), snapshot.memorisedBySurah)
    }

    @Test
    fun aSkippedCompletionDoesNotCountTowardsMemorisation() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)
        advanceDays(1)
        progress.hifzCompleted(ayah, skips = 1)
        advanceDays(1)
        progress.hifzCompleted(ayah, skips = 0)

        assertEquals(emptySet<AyahRef>(), progress.snapshot().memorisedByAyah)
    }

    @Test
    fun theThresholdIsAdjustableInSettings() = runBlocking {
        progress.hifzCompleted(ayah, skips = 0)
        advanceDays(1)
        progress.hifzCompleted(ayah, skips = 0)
        assertEquals(0, progress.snapshot().memorisedCount)

        settings.setMemorisationThreshold(2)

        assertEquals(1, progress.snapshot().memorisedCount)
        assertEquals(2, progress.snapshot().memorisationThreshold)
    }

    @Test
    fun theThresholdStaysInsideItsRange() = runBlocking {
        settings.setMemorisationThreshold(0)
        assertEquals(
            LearnSettings.MIN_MEMORISATION_THRESHOLD,
            progress.snapshot().memorisationThreshold,
        )
        settings.setMemorisationThreshold(99)
        assertEquals(
            LearnSettings.MAX_MEMORISATION_THRESHOLD,
            progress.snapshot().memorisationThreshold,
        )
    }

    @Test
    fun aCardIsDueOnceItsIntervalHasPassed() = runBlocking {
        progress.rateCard(ayah, FsrsRating.Good)
        assertEquals(emptyList<AyahRef>(), progress.snapshot().dueToday)

        advanceDays(30)

        assertEquals(listOf(ayah), progress.snapshot().dueToday)
    }

    @Test
    fun pagesAndStreakComeFromEvents() = runBlocking {
        progress.pageOpened(10)
        progress.pageOpened(10)
        progress.pageOpened(11)
        advanceDays(1)
        progress.ayahTrackedGreen(ayah)

        val snapshot = progress.snapshot()

        assertEquals(2, snapshot.streakDays)
        assertEquals(2, snapshot.pagesThisWeek)
        assertEquals(0, snapshot.pagesToday)
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
