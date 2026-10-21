package app.awn.quran.feature.learn.progress

import androidx.room.withTransaction
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.fsrs.FsrsCard
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.fsrs.FsrsScheduler
import app.awn.quran.core.data.fsrs.FsrsState
import app.awn.quran.feature.learn.data.CardEntity
import app.awn.quran.feature.learn.data.EventEntity
import app.awn.quran.feature.learn.data.EventKind
import app.awn.quran.feature.learn.data.LearnDatabase
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
// Each function is one entry point that a screen, the recite feature or the reminder calls.
@Suppress("TooManyFunctions")
class LearnProgress(
    private val database: LearnDatabase,
    private val settings: LearnSettings,
    private val clock: LearnClock = LearnClock.System,
    private val scheduler: FsrsScheduler = dailyScheduler(),
) : ProgressEvents {

    private val dao = database.dao()

    // The card as it was before the recitation rated it, so a grade from the user can replace it.
    private class AutomaticRating(val cardBefore: CardEntity?, val at: Long)

    private val automaticRatings = mutableMapOf<AyahRef, AutomaticRating>()

    override suspend fun pageOpened(page: Int) {
        record(EventKind.PAGE_OPENED, ayah = null, page = page)
    }

    override suspend fun ayahTrackedGreen(ayah: AyahRef) {
        record(EventKind.AYAH_GREEN, ayah = ayah, page = null)
    }

    /**
     * Rates the completion for FSRS: no skips is Good, one skip Hard, two or more Again. The user
     * can replace this rating with [gradeAyah] for a short while.
     */
    override suspend fun hifzCompleted(ayah: AyahRef, skips: Int) {
        val isClean = skips == 0
        val kind = if (isClean) EventKind.HIFZ_CLEAN else EventKind.HIFZ_WITH_SKIPS
        val rating =
            when {
                isClean -> FsrsRating.Good
                skips == 1 -> FsrsRating.Hard
                else -> FsrsRating.Again
            }
        database.withTransaction {
            record(kind, ayah = ayah, page = null)
            val previousCard = dao.findCard(ayah.surah, ayah.ayah)
            reviewCard(ayah, rating)
            automaticRatings[ayah] = AutomaticRating(previousCard, clock.now().toEpochMilli())
        }
    }

    /**
     * Rates [ayah] with the user's own grade. When the recitation rated the same ayah in the last
     * [AUTOMATIC_RATING_WINDOW_MILLIS], that rating is undone first so one recitation counts as one
     * review; otherwise this is a plain rating.
     */
    suspend fun gradeAyah(ayah: AyahRef, rating: FsrsRating) {
        database.withTransaction {
            val automatic = automaticRatings.remove(ayah)
            val isRecent =
                automatic != null &&
                    clock.now().toEpochMilli() - automatic.at <= AUTOMATIC_RATING_WINDOW_MILLIS
            if (automatic != null && isRecent) {
                val previousCard = automatic.cardBefore
                if (previousCard == null) {
                    dao.deleteCard(ayah.surah, ayah.ayah)
                } else {
                    dao.upsertCard(previousCard)
                }
            }
            record(EventKind.REVISION_RATED, ayah = ayah, page = null)
            reviewCard(ayah, rating)
        }
    }

    /** Ayahs due today or earlier, in reading order. */
    suspend fun dueAyahs(): List<AyahRef> {
        val zone = clock.zone()
        val today = clock.now().atZone(zone).toLocalDate()
        return ProgressCalculator.dueToday(dao.observeCards().first(), today, zone)
            .map { AyahRef(it.surah, it.ayah) }
            .sortedWith(compareBy({ it.surah }, { it.ayah }))
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

private const val AUTOMATIC_RATING_WINDOW_MILLIS = 10 * 60 * 1000L
