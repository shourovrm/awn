package app.awn.quran.feature.hifz.method

import java.time.LocalDate
import java.time.ZoneId

/**
 * A group of pages to revise today. [donePages] are the ones already graded today; a revision saved
 * before its last page leaves the rest on Today.
 */
data class PageGroup(val pages: List<Int>, val donePages: Set<Int>) {
    constructor(
        pages: List<Int>,
        isDone: Boolean,
    ) : this(pages, if (isDone) pages.toSet() else emptySet())

    val isDone: Boolean
        get() = donePages.containsAll(pages)

    /** The pages still to revise today, in page order. */
    val pagesLeft: List<Int>
        get() = pages.filter { it !in donePages }
}

/**
 * Today's new lesson; [isDone] once the user finished all of it today. [isContinuation] marks a
 * lesson the user already saved part of today, so the tab can say how much is left.
 */
data class AssignedLesson(
    val part: LessonPart,
    val isDone: Boolean,
    val isContinuation: Boolean = false,
)

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
 * that its pages were not revised, so the pages that waited longest come first the next time and
 * the load of one day never grows past the limit.
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
        val due = DueRevision(settings.revisionSpacing, today, zone)
        return when (plan.method) {
            HifzMethodId.SabaqSabqiManzil ->
                splitRevision(plan, lesson, records, finishedToday, due)
            else -> singleRevision(plan, lesson, records, finishedToday, due)
        }
    }

    /**
     * A lesson finished in full today is shown done. Otherwise the next lesson starts at the
     * cursor, which a partly saved lesson has moved to its first Not yet ayah.
     */
    @Suppress("ReturnCount") // A finished lesson, then no lesson left, then the next lesson.
    private fun assignLesson(
        plan: HifzPlan,
        table: PageTable,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
    ): AssignedLesson? {
        val savedToday = finishedToday.filter { it.kind == PartKind.NewLesson }
        val completed = savedToday.lastOrNull { it.isComplete }
        val firstSaved = savedToday.firstOrNull()?.firstAyah
        val completedLast = completed?.lastAyah
        if (firstSaved != null && completedLast != null) {
            return AssignedLesson(LessonPlanner.partBetween(table, firstSaved, completedLast), true)
        }
        val part =
            LessonPlanner.nextPart(table, lessonSizeOf(plan), plan.cursor) {
                records[it]?.status ?: PageStatus.NotStarted
            } ?: return null
        return AssignedLesson(part, isDone = false, isContinuation = savedToday.isNotEmpty())
    }

    private fun lessonSizeOf(plan: HifzPlan): LessonSize =
        when (plan.method) {
            HifzMethodId.SabaqSabqiManzil -> plan.settings.lessonSize
            else -> LessonSize.Ayahs(plan.settings.ayahsPerDay)
        }

    /**
     * Today's group of [kind] as first given, with the pages graded since; null before any save.
     */
    private fun savedGroup(kind: PartKind, finishedToday: List<FinishedPart>): PageGroup? {
        val saved = finishedToday.filter { it.kind == kind }
        if (saved.isEmpty()) return null
        return PageGroup(saved.first().pages, saved.flatMap { it.gradedPages }.toSet())
    }

    private fun splitRevision(
        plan: HifzPlan,
        lesson: AssignedLesson?,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
        due: DueRevision,
    ): DayAssignment {
        val settings = plan.settings
        val limit = settings.revisionPageLimit
        val windowStart = due.today.minusDays(settings.recentDays.toLong())

        val recent =
            savedGroup(PartKind.Recent, finishedToday)
                ?: records.values
                    .filter { isRecent(it, windowStart, due.today) }
                    .sortedWith(compareByDescending<PageRecord> { it.learnedOn }.thenBy { it.page })
                    .take(limit)
                    .map { it.page }
                    .sorted()
                    .let { PageGroup(it, emptySet()) }

        val oldCandidates =
            records.values.filter { record ->
                record.status.isKnown && (record.learnedOn?.let { it < windowStart } ?: true)
            }
        val old =
            savedGroup(PartKind.Old, finishedToday)
                ?: due.pick(oldCandidates, settings, limit - recent.pages.size).let {
                    PageGroup(it, emptySet())
                }

        return DayAssignment(
            newLesson = lesson,
            recent = recent.takeIf { it.pages.isNotEmpty() },
            old = old.takeIf { it.pages.isNotEmpty() },
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

    @Suppress("LongParameterList")
    private fun singleRevision(
        plan: HifzPlan,
        lesson: AssignedLesson?,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
        due: DueRevision,
    ): DayAssignment {
        val limit = plan.settings.revisionPageLimit
        val candidates =
            records.values.filter { record ->
                val taughtToday = record.learnedOn == due.today
                !taughtToday && (record.status.isKnown || record.card != null)
            }
        val old =
            savedGroup(PartKind.Old, finishedToday)
                ?: PageGroup(due.pick(candidates, plan.settings, limit), emptySet())
        return DayAssignment(
            newLesson = lesson,
            recent = null,
            old = old.takeIf { it.pages.isNotEmpty() },
            revisionPageLimit = limit,
        )
    }
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
