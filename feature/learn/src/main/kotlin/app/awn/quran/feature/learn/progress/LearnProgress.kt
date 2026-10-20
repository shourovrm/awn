package app.awn.quran.feature.learn.progress

import androidx.room.withTransaction
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.feature.learn.data.CardEntity
import app.awn.quran.feature.learn.data.EventEntity
import app.awn.quran.feature.learn.data.EventKind
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.fsrs.FsrsCard
import app.awn.quran.feature.learn.fsrs.FsrsRating
import app.awn.quran.feature.learn.fsrs.FsrsScheduler
import app.awn.quran.feature.learn.fsrs.FsrsState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The current time and the device time zone, as functions so a test can move both. */
class LearnClock(val now: () -> Instant, val zone: () -> ZoneId) {
    companion object {
        /** Asks the system each time, so a change of time zone takes effect without a restart. */
        val System = LearnClock(now = { Instant.now() }, zone = { ZoneId.systemDefault() })
    }
}

/** Everything the Learn and Progress screens show, computed at one moment. */
data class ProgressSnapshot(
    val streakDays: Int,
    val pagesToday: Int,
    val pagesThisWeek: Int,
    val memorisedByAyah: Set<AyahRef>,
    val memorisedBySurah: Map<Int, Int>,
    val memorisationThreshold: Int,
    val dueToday: List<AyahRef>,
    val nextDue: Instant?,
) {
    val memorisedCount: Int
        get() = memorisedByAyah.size
}

/**
 * Records learning events in [LearnDatabase], schedules ayah revision with FSRS and derives the
 * progress figures. :feature:read and :feature:recite receive this class as [ProgressEvents].
 */
class LearnProgress(
    private val database: LearnDatabase,
    private val settings: LearnSettings,
    private val clock: LearnClock = LearnClock.System,
    private val scheduler: FsrsScheduler = dailyScheduler(),
) : ProgressEvents {

    private val dao = database.dao()

    override suspend fun pageOpened(page: Int) {
        record(EventKind.PAGE_OPENED, ayah = null, page = page)
    }

    override suspend fun ayahTrackedGreen(ayah: AyahRef) {
        record(EventKind.AYAH_GREEN, ayah = ayah, page = null)
    }

    /** A completion without skips is rated Good for FSRS; one with skips is rated Again. */
    override suspend fun hifzCompleted(ayah: AyahRef, skips: Int) {
        val isClean = skips == 0
        val kind = if (isClean) EventKind.HIFZ_CLEAN else EventKind.HIFZ_WITH_SKIPS
        val rating = if (isClean) FsrsRating.Good else FsrsRating.Again
        database.withTransaction {
            record(kind, ayah = ayah, page = null)
            reviewCard(ayah, rating)
        }
    }

    /**
     * Rates one revision of [ayah]. The rating comes from the recite feature once it can judge a
     * recitation; until then the Revision screen offers Again and Good by hand.
     */
    suspend fun rateCard(ayah: AyahRef, rating: FsrsRating) {
        database.withTransaction {
            record(EventKind.REVISION_RATED, ayah = ayah, page = null)
            reviewCard(ayah, rating)
        }
    }

    suspend fun snapshot(): ProgressSnapshot =
        buildSnapshot(
            cards = dao.observeCards().first(),
            threshold = settings.memorisationThreshold.first(),
        )

    /** Emits a new snapshot whenever an event, a card or the threshold changes. */
    fun observeSnapshot(): Flow<ProgressSnapshot> =
        combine(dao.observeEventCount(), dao.observeCards(), settings.memorisationThreshold) {
                _,
                cards,
                threshold ->
                cards to threshold
            }
            .map { (cards, threshold) -> buildSnapshot(cards, threshold) }

    private suspend fun record(kind: String, ayah: AyahRef?, page: Int?) {
        val event =
            EventEntity(
                kind = kind,
                surah = ayah?.surah,
                ayah = ayah?.ayah,
                page = page,
                at = clock.now().toEpochMilli(),
            )
        dao.insertEvent(event)
    }

    private suspend fun reviewCard(ayah: AyahRef, rating: FsrsRating) {
        val nowMillis = clock.now().toEpochMilli()
        val existing = dao.findCard(ayah.surah, ayah.ayah)
        val before = existing?.toFsrsCard() ?: scheduler.newCard(nowMillis)
        val after = scheduler.reviewCard(before, rating, nowMillis)
        val lapsed = existing != null && existing.state == FsrsState.Review.value
        dao.upsertCard(
            CardEntity(
                surah = ayah.surah,
                ayah = ayah.ayah,
                stability = checkNotNull(after.stability),
                difficulty = checkNotNull(after.difficulty),
                due = after.dueMillis,
                lastReview = nowMillis,
                reps = (existing?.reps ?: 0) + 1,
                lapses =
                    (existing?.lapses ?: 0) + if (lapsed && rating == FsrsRating.Again) 1 else 0,
                state = after.state.value,
                step = after.step,
            ),
        )
    }

    private suspend fun buildSnapshot(cards: List<CardEntity>, threshold: Int): ProgressSnapshot {
        val zone = clock.zone()
        val today = clock.now().atZone(zone).toLocalDate()
        val pageOpens = dao.pageOpens()
        val memorised =
            ProgressCalculator.memorisedAyahs(dao.cleanHifzCompletions(), threshold, zone)
        return ProgressSnapshot(
            streakDays = ProgressCalculator.currentStreak(dao.allEventTimes(), today, zone),
            pagesToday = ProgressCalculator.pagesPerDay(pageOpens, zone)[today] ?: 0,
            pagesThisWeek = ProgressCalculator.pagesThisWeek(pageOpens, today, zone),
            memorisedByAyah = memorised,
            memorisedBySurah = ProgressCalculator.memorisedBySurah(memorised),
            memorisationThreshold = threshold,
            dueToday =
                ProgressCalculator.dueToday(cards, today, zone).map { AyahRef(it.surah, it.ayah) },
            nextDue = ProgressCalculator.nextDue(cards),
        )
    }
}

/**
 * Revision happens once a day at most, so minute-long learning steps would only list a fresh or
 * forgotten ayah as due again within the hour. Without steps FSRS schedules in whole days, with a
 * first interval of one day or more.
 */
private fun dailyScheduler() =
    FsrsScheduler(learningStepsMillis = emptyList(), relearningStepsMillis = emptyList())

private fun CardEntity.toFsrsCard(): FsrsCard =
    FsrsCard(
        state = FsrsState.entries.first { it.value == state },
        step = step,
        stability = stability,
        difficulty = difficulty,
        dueMillis = due,
        lastReviewMillis = lastReview,
    )
