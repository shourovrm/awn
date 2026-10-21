package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsCard
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.fsrs.FsrsScheduler
import app.awn.quran.core.data.fsrs.FsrsState
import java.time.LocalDate

/** The new state of the pages after a lesson, and where the next lesson starts. */
data class LessonOutcome(val records: List<PageRecord>, val nextCursor: AyahRef?)

/** How finishing a lesson or a revision changes what the app knows of each page. */
object PageProgressRules {

    /**
     * Memory strength in days from which a page counts as Solid. About four good reviews at a
     * week's spacing reach it, which is when a page needs no special care any more.
     */
    const val SOLID_STABILITY_DAYS = 21.0

    private const val MILLIS_PER_DAY = 86_400_000L

    /** Minute-long learning steps make no sense for pages, so a new card is due after days. */
    fun dailyScheduler(): FsrsScheduler =
        FsrsScheduler(learningStepsMillis = emptyList(), relearningStepsMillis = emptyList())

    fun ratingOf(grade: PartGrade): FsrsRating =
        when (grade) {
            PartGrade.Forgot -> FsrsRating.Again
            PartGrade.Hard -> FsrsRating.Hard
            PartGrade.Good -> FsrsRating.Good
            PartGrade.Easy -> FsrsRating.Easy
        }

    /**
     * Applies a graded new lesson. A forgotten lesson leaves the pages Learning and the cursor
     * where it was, so the same lesson comes back. Any other grade moves the cursor past the
     * lesson; a page whose last ayah is inside the lesson becomes Memorised, a page that is only
     * partly covered stays Learning.
     */
    // The lesson, the day, the time, the records and the scheduler are all inputs of one rule.
    @Suppress("LongParameterList")
    fun completeLesson(
        table: PageTable,
        part: LessonPart,
        grade: PartGrade,
        today: LocalDate,
        nowMillis: Long,
        records: Map<Int, PageRecord>,
        scheduler: FsrsScheduler = dailyScheduler(),
    ): LessonOutcome {
        val updated =
            (part.firstPage..part.lastPage).map { page ->
                val existing = records[page] ?: PageRecord(page, PageStatus.NotStarted)
                val isComplete = table.ayahsOn(page).all { it <= part.last }
                when {
                    grade == PartGrade.Forgot || !isComplete -> startLearning(existing, today)
                    else -> memorise(existing, grade, today, nowMillis, scheduler)
                }
            }
        val cursor = if (grade == PartGrade.Forgot) part.first else table.ayahAfter(part.last)
        return LessonOutcome(updated, cursor)
    }

    private fun startLearning(existing: PageRecord, today: LocalDate): PageRecord =
        if (existing.status.isKnown) existing.copy(learnedOn = today)
        else existing.copy(status = PageStatus.Learning, learnedOn = today)

    private fun memorise(
        existing: PageRecord,
        grade: PartGrade,
        today: LocalDate,
        nowMillis: Long,
        scheduler: FsrsScheduler,
    ): PageRecord {
        val card =
            scheduler.reviewCard(
                existing.card ?: scheduler.newCard(nowMillis),
                ratingOf(grade),
                nowMillis,
            )
        return existing.copy(
            status = statusFor(card, existing.status),
            learnedOn = today,
            lastRevisedOn = today,
            card = card,
        )
    }

    /**
     * Applies a graded revision of one page. Forgot sends the page back to Learning and makes it a
     * recent lesson again. A Learning page without a card is half a lesson, which revision does not
     * finish, so only its revision date changes.
     */
    // Guards come first: a half lesson changes only its date, a forgotten page restarts.
    @Suppress("ReturnCount")
    fun gradeRevision(
        record: PageRecord,
        grade: PartGrade,
        today: LocalDate,
        nowMillis: Long,
        scheduler: FsrsScheduler = dailyScheduler(),
    ): PageRecord {
        val isHalfLesson = record.status == PageStatus.Learning && record.card == null
        if (isHalfLesson) return record.copy(lastRevisedOn = today)
        val card =
            scheduler.reviewCard(
                record.card ?: scheduler.newCard(nowMillis),
                ratingOf(grade),
                nowMillis,
            )
        if (grade == PartGrade.Forgot) {
            return record.copy(
                status = PageStatus.Learning,
                learnedOn = today,
                lastRevisedOn = today,
                card = card,
            )
        }
        return record.copy(
            status = statusFor(card, PageStatus.Memorised),
            lastRevisedOn = today,
            card = card,
        )
    }

    private fun statusFor(card: FsrsCard, current: PageStatus): PageStatus {
        val stability =
            card.stability ?: return current.takeIf { it.isKnown } ?: PageStatus.Memorised
        return if (stability >= SOLID_STABILITY_DAYS) PageStatus.Solid else PageStatus.Memorised
    }

    /**
     * What the user says they know about a page. Memorised starts the memory state with one Good
     * review so the page joins the revision; Solid starts it at [SOLID_STABILITY_DAYS]; Learning
     * and Not started keep no memory state. A page marked this way is an old page, not a recent
     * lesson, so it never floods the recent revision.
     */
    fun markPage(
        page: Int,
        status: PageStatus,
        today: LocalDate,
        nowMillis: Long,
        scheduler: FsrsScheduler = dailyScheduler(),
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

    private const val SOLID_DIFFICULTY = 5.0

    private fun solidCard(nowMillis: Long): FsrsCard =
        FsrsCard(
            state = FsrsState.Review,
            stability = SOLID_STABILITY_DAYS,
            difficulty = SOLID_DIFFICULTY,
            dueMillis = nowMillis + SOLID_STABILITY_DAYS.toLong() * MILLIS_PER_DAY,
            lastReviewMillis = nowMillis,
        )
}
