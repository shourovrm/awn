package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.fsrs.FsrsCard
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.fsrs.FsrsScheduler
import app.awn.quran.core.data.fsrs.FsrsState
import java.time.LocalDate

/** What marking a page by hand ("Mark what I know", the map's page sheet) writes. */
object PageMarking {
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val SOLID_DIFFICULTY = 5.0

    /** Pages marked together come back over at most this many days, not all on one day. */
    const val SPREAD_DAYS = 30

    /**
     * What the user says they know about a page. A page that already has a memory and stays known
     * keeps its dates and memory, so marking it again loses nothing; only its status changes, and
     * Solid lifts a weaker memory to [PageProgressRules.SOLID_STABILITY_DAYS]. A newly known page
     * starts a memory (Memorised as one Good review, Solid at three weeks) and is first due in
     * [firstRevisionInDays] when given. Learning and Not started keep no memory. A page marked this
     * way is an old page, not a recent lesson, so it never floods the recent revision.
     */
    // The page, its new state, the day, the time, its old record and its first day belong together.
    @Suppress("LongParameterList")
    fun markPage(
        page: Int,
        status: PageStatus,
        today: LocalDate,
        nowMillis: Long,
        existing: PageRecord? = null,
        firstRevisionInDays: Int? = null,
        scheduler: FsrsScheduler = PageProgressRules.dailyScheduler(),
    ): PageRecord {
        val keepsHistory =
            existing != null && existing.status.isKnown && status.isKnown && existing.card != null
        if (keepsHistory) return relabel(checkNotNull(existing), status, nowMillis)
        val fresh = freshMark(page, status, today, nowMillis, scheduler)
        val card = fresh.card
        return if (firstRevisionInDays == null || card == null) {
            fresh
        } else {
            fresh.copy(
                card = card.copy(dueMillis = nowMillis + firstRevisionInDays * MILLIS_PER_DAY),
            )
        }
    }

    /**
     * The first revision day of page [index] of [count] marked together, from 1 to [SPREAD_DAYS].
     */
    fun spreadDays(index: Int, count: Int): Int = 1 + index * minOf(count, SPREAD_DAYS) / count

    private fun relabel(existing: PageRecord, status: PageStatus, nowMillis: Long): PageRecord {
        val stability = existing.card?.stability ?: 0.0
        val needsSolidMemory =
            status == PageStatus.Solid && stability < PageProgressRules.SOLID_STABILITY_DAYS
        return if (needsSolidMemory) existing.copy(status = status, card = solidCard(nowMillis))
        else existing.copy(status = status)
    }

    private fun freshMark(
        page: Int,
        status: PageStatus,
        today: LocalDate,
        nowMillis: Long,
        scheduler: FsrsScheduler,
    ): PageRecord =
        when (status) {
            PageStatus.NotStarted -> PageRecord(page, PageStatus.NotStarted)
            PageStatus.Learning -> PageRecord(page, PageStatus.Learning, learnedOn = today)
            PageStatus.Memorised ->
                PageRecord(
                    page,
                    PageStatus.Memorised,
                    card =
                        scheduler.reviewCard(
                            scheduler.newCard(nowMillis),
                            FsrsRating.Good,
                            nowMillis,
                        ),
                )
            PageStatus.Solid -> PageRecord(page, PageStatus.Solid, card = solidCard(nowMillis))
        }

    private fun solidCard(nowMillis: Long): FsrsCard =
        FsrsCard(
            state = FsrsState.Review,
            stability = PageProgressRules.SOLID_STABILITY_DAYS,
            difficulty = SOLID_DIFFICULTY,
            dueMillis =
                nowMillis + PageProgressRules.SOLID_STABILITY_DAYS.toLong() * MILLIS_PER_DAY,
            lastReviewMillis = nowMillis,
        )
}
