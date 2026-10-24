package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsCard
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.data.fsrs.FsrsScheduler
import java.time.LocalDate

/** The new state of the pages after a lesson, and where the next lesson starts. */
data class LessonOutcome(
    val records: List<PageRecord>,
    val nextCursor: AyahRef?,
    /** True when every ayah of the lesson was memorised, so the lesson is done for today. */
    val isComplete: Boolean,
)

/** How finishing a lesson or a revision changes what the app knows of each page. */
object PageProgressRules {

    /**
     * Memory strength in days from which a page counts as Solid. About four good reviews at a
     * week's spacing reach it, which is when a page needs no special care any more.
     */
    const val SOLID_STABILITY_DAYS = 21.0

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
     * Applies a saved new lesson. [firmness] holds what the user said of each ayah of [part]; an
     * ayah without an entry counts as Not yet. The ayahs memorised in a row from the lesson's first
     * ayah move the lesson on: the cursor goes to the first Not yet ayah, or past the lesson when
     * there is none, so a lesson saved at ayah 4 of 7 starts at ayah 5 next time. A page whose
     * ayahs all lie inside that run becomes Memorised, graded by its weakest ayah of the lesson;
     * every other page of the lesson is Learning.
     */
    // The lesson, the answers, the day, the time, the records and the scheduler are all inputs of
    // one rule.
    @Suppress("LongParameterList")
    fun completeLesson(
        table: PageTable,
        part: LessonPart,
        firmness: Map<AyahRef, AyahFirmness>,
        today: LocalDate,
        nowMillis: Long,
        records: Map<Int, PageRecord>,
        scheduler: FsrsScheduler = dailyScheduler(),
    ): LessonOutcome {
        val lessonAyahs = table.allAyahs.filter { it in part.first..part.last }
        val memorisedRun = lessonAyahs.takeWhile {
            (firmness[it] ?: AyahFirmness.NotYet).isMemorised
        }
        val runEnd = memorisedRun.lastOrNull()
        val updated =
            (part.firstPage..part.lastPage).map { page ->
                val existing = records[page] ?: PageRecord(page, PageStatus.NotStarted)
                val pageAyahs = table.ayahsOn(page)
                val isComplete = runEnd != null && pageAyahs.all { it <= runEnd }
                if (isComplete) {
                    val weakest =
                        memorisedRun.filter { it in pageAyahs }.minOf { firmness.getValue(it) }
                    memorise(existing, checkNotNull(weakest.pageGrade), today, nowMillis, scheduler)
                } else {
                    startLearning(existing, today)
                }
            }
        val firstNotYet = lessonAyahs.getOrNull(memorisedRun.size)
        val cursor = firstNotYet ?: table.ayahAfter(part.last)
        return LessonOutcome(updated, cursor, isComplete = firstNotYet == null)
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
            spacingStep = 0,
        )
    }

    /** The next fixed-spacing step after [grade]; the schedule caps it at its last step. */
    fun nextSpacingStep(current: Int, grade: PartGrade): Int =
        when (grade) {
            PartGrade.Forgot -> 0
            PartGrade.Hard -> current
            PartGrade.Good -> current + 1
            PartGrade.Easy -> current + 2
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
                spacingStep = 0,
            )
        }
        return record.copy(
            status = statusFor(card, PageStatus.Memorised),
            lastRevisedOn = today,
            card = card,
            spacingStep = nextSpacingStep(record.spacingStep, grade),
        )
    }

    private fun statusFor(card: FsrsCard, current: PageStatus): PageStatus {
        val stability =
            card.stability ?: return current.takeIf { it.isKnown } ?: PageStatus.Memorised
        return if (stability >= SOLID_STABILITY_DAYS) PageStatus.Solid else PageStatus.Memorised
    }
}
