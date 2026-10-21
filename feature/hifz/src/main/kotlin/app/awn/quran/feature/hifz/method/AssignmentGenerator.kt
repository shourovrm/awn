package app.awn.quran.feature.hifz.method

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A group of pages to revise today; [isDone] once the user finished it today. */
data class PageGroup(val pages: List<Int>, val isDone: Boolean)

/** Today's new lesson; [isDone] once the user finished it today. */
data class AssignedLesson(val part: LessonPart, val isDone: Boolean)

/**
 * What the plan asks for today. [recent] and [old] are the revision, [revisionPageLimit] the most
 * pages they may hold together. For methods with no recent/old split, [old] holds all revision.
 */
data class DayAssignment(
    val newLesson: AssignedLesson?,
    val recent: PageGroup?,
    val old: PageGroup?,
    val revisionPageLimit: Int,
) {
    val revisionPageCount: Int
        get() = (recent?.pages?.size ?: 0) + (old?.pages?.size ?: 0)

    /** The part the primary button starts: the first one not done, in lesson, recent, old order. */
    fun nextPart(): PartKind? =
        when {
            newLesson != null && !newLesson.isDone -> PartKind.NewLesson
            recent != null && !recent.isDone -> PartKind.Recent
            old != null && !old.isDone -> PartKind.Old
            else -> null
        }

    companion object {
        fun empty(limit: Int) = DayAssignment(null, null, null, limit)
    }
}

/**
 * Builds today's assignment. Nothing is carried over from earlier days: a missed day only means
 * that its pages were not revised, so the oldest-revised pages come first the next time and the
 * load of one day never grows past the limit.
 */
object AssignmentGenerator {

    // Plan, print, records, log, day and zone are the inputs of one pure function.
    @Suppress("LongParameterList")
    fun generate(
        plan: HifzPlan,
        table: PageTable,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DayAssignment {
        val settings = plan.settings
        if (plan.method == HifzMethodId.TrackOnly)
            return DayAssignment.empty(settings.revisionPageLimit)

        val lesson = assignLesson(plan, table, records, finishedToday)
        return when (plan.method) {
            HifzMethodId.SabaqSabqiManzil ->
                splitRevision(plan, lesson, records, finishedToday, today)
            else -> dueRevision(plan, lesson, records, finishedToday, today, zone)
        }
    }

    // A finished lesson, then no lesson left, then the next lesson: three exits by design.
    @Suppress("ReturnCount")
    private fun assignLesson(
        plan: HifzPlan,
        table: PageTable,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
    ): AssignedLesson? {
        val done = finishedToday.lastOrNull { it.kind == PartKind.NewLesson }
        val doneFirst = done?.firstAyah
        val doneLast = done?.lastAyah
        if (doneFirst != null && doneLast != null) {
            return AssignedLesson(LessonPlanner.partBetween(table, doneFirst, doneLast), true)
        }
        val part =
            LessonPlanner.nextPart(table, lessonSizeOf(plan), plan.cursor) {
                records[it]?.status ?: PageStatus.NotStarted
            } ?: return null
        return AssignedLesson(part, false)
    }

    private fun lessonSizeOf(plan: HifzPlan): LessonSize =
        when (plan.method) {
            HifzMethodId.SabaqSabqiManzil -> plan.settings.lessonSize
            else -> LessonSize.Ayahs(plan.settings.ayahsPerDay)
        }

    private fun splitRevision(
        plan: HifzPlan,
        lesson: AssignedLesson?,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
        today: LocalDate,
    ): DayAssignment {
        val settings = plan.settings
        val limit = settings.revisionPageLimit
        val windowStart = today.minusDays(settings.recentDays.toLong())

        val doneRecent = finishedToday.lastOrNull { it.kind == PartKind.Recent }
        val recentPages =
            doneRecent?.pages
                ?: records.values
                    .filter { isRecent(it, windowStart, today) }
                    .sortedWith(compareByDescending<PageRecord> { it.learnedOn }.thenBy { it.page })
                    .take(limit)
                    .map { it.page }
                    .sorted()

        val doneOld = finishedToday.lastOrNull { it.kind == PartKind.Old }
        val oldPages =
            doneOld?.pages ?: oldPagesFor(records, windowStart, settings, limit - recentPages.size)

        return DayAssignment(
            newLesson = lesson,
            recent =
                recentPages.takeIf { it.isNotEmpty() }?.let { PageGroup(it, doneRecent != null) },
            old = oldPages.takeIf { it.isNotEmpty() }?.let { PageGroup(it, doneOld != null) },
            revisionPageLimit = limit,
        )
    }

    // A page taught today is not revised on the day it was taught.
    private fun isRecent(record: PageRecord, windowStart: LocalDate, today: LocalDate): Boolean {
        val learnedOn = record.learnedOn ?: return false
        val isInProgress =
            record.status == PageStatus.Learning || record.status == PageStatus.Memorised
        return isInProgress && learnedOn >= windowStart && learnedOn < today
    }

    // The oldest-revised pages first, as many as one cycle needs per day, never past the room left.
    @Suppress("ReturnCount")
    private fun oldPagesFor(
        records: Map<Int, PageRecord>,
        windowStart: LocalDate,
        settings: MethodSettings,
        room: Int,
    ): List<Int> {
        if (room <= 0) return emptyList()
        val old =
            records.values.filter { record ->
                record.status.isKnown && (record.learnedOn?.let { it < windowStart } ?: true)
            }
        if (old.isEmpty()) return emptyList()
        val perDay = (old.size + settings.manzilCycleDays - 1) / settings.manzilCycleDays
        return old.sortedWith(
                compareBy<PageRecord, LocalDate?>(nullsFirst()) { it.lastRevisedOn }
                    .thenBy { it.page },
            )
            .take(minOf(perDay, room))
            .map { it.page }
            .sorted()
    }

    @Suppress("LongParameterList")
    private fun dueRevision(
        plan: HifzPlan,
        lesson: AssignedLesson?,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
        today: LocalDate,
        zone: ZoneId,
    ): DayAssignment {
        val limit = plan.settings.revisionPageLimit
        val doneOld = finishedToday.lastOrNull { it.kind == PartKind.Old }
        val pages =
            doneOld?.pages
                ?: records.values
                    .filter { record -> isDue(record, today, zone) }
                    .sortedWith(compareBy<PageRecord> { dueDay(it, zone) }.thenBy { it.page })
                    .take(limit)
                    .map { it.page }
                    .sorted()
        return DayAssignment(
            newLesson = lesson,
            recent = null,
            old = pages.takeIf { it.isNotEmpty() }?.let { PageGroup(it, doneOld != null) },
            revisionPageLimit = limit,
        )
    }

    private fun isDue(record: PageRecord, today: LocalDate, zone: ZoneId): Boolean {
        val card = record.card ?: return false
        val taughtToday = record.learnedOn == today
        return record.status != PageStatus.NotStarted &&
            !taughtToday &&
            Instant.ofEpochMilli(card.dueMillis).atZone(zone).toLocalDate() <= today
    }

    private fun dueDay(record: PageRecord, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(checkNotNull(record.card).dueMillis).atZone(zone).toLocalDate()
}

/** Rough minutes for a part, shown to the user as a guide. */
object PartMinutes {
    private const val MINUTES_PER_REVISION_PAGE = 3
    private const val MINUTES_PER_LESSON_PAGE = 20
    private const val MINUTES_PER_LESSON_AYAH = 2
    private const val MINUTES_MINIMUM = 5

    fun forRevision(pageCount: Int): Int = pageCount * MINUTES_PER_REVISION_PAGE

    /** A lesson takes time by its ayahs, at most a page's worth. */
    fun forLesson(ayahCount: Int): Int =
        (ayahCount * MINUTES_PER_LESSON_AYAH).coerceIn(MINUTES_MINIMUM, MINUTES_PER_LESSON_PAGE)
}
