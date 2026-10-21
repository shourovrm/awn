package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.AssignmentGenerator
import app.awn.quran.feature.hifz.method.DayAssignment
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.HifzPlan
import app.awn.quran.feature.hifz.method.HifzProgress
import app.awn.quran.feature.hifz.method.LessonPart
import app.awn.quran.feature.hifz.method.MethodDefaults
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageTable
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.method.ProgressMap
import app.awn.quran.feature.hifz.method.WeakSpot
import app.awn.quran.feature.hifz.method.WeakSpots
import java.time.LocalDate

/** Everything the Hifz tab draws once a plan exists. */
data class HifzState(
    val plan: HifzPlan,
    val assignment: DayAssignment,
    val progressMap: ProgressMap,
    val weakSpots: List<WeakSpot>,
    /** Page by page, for the surah and juz pickers of "Mark what I know". */
    val table: PageTable,
)

/** What the user is practising on the Quran screen. */
data class PracticeRequest(
    val kind: PartKind,
    val method: HifzMethodId,
    val pages: List<Int>,
    val firstAyah: AyahRef,
    /** The last ayah of the part: the lesson's last ayah, or the last ayah on the last page. */
    val lastAyah: AyahRef,
    val ayahCount: Int,
    val repeatCount: Int,
    /** The lesson itself, kept so that finishing it records the same ayahs that were shown. */
    val lesson: LessonPart?,
)

object HifzStateBuilder {

    fun build(
        plan: HifzPlan,
        table: PageTable,
        records: Map<Int, PageRecord>,
        finishedToday: List<FinishedPart>,
        mistakes: List<MistakeRecord>,
        today: LocalDate,
    ): HifzState =
        HifzState(
            plan = plan,
            assignment = AssignmentGenerator.generate(plan, table, records, finishedToday, today),
            progressMap = HifzProgress.map(table, records),
            weakSpots = WeakSpots.of(mistakes),
            table = table,
        )

    /** The practice for [kind] today, or null when the assignment has no such part. */
    fun practiceFor(state: HifzState, kind: PartKind): PracticeRequest? {
        val assignment = state.assignment
        val method = state.plan.method
        val repeat = MethodDefaults.repeatCountFor(method)
        return when (kind) {
            PartKind.NewLesson ->
                assignment.newLesson?.part?.let { part ->
                    PracticeRequest(
                        kind = kind,
                        method = method,
                        pages = (part.firstPage..part.lastPage).toList(),
                        firstAyah = part.first,
                        lastAyah = part.last,
                        ayahCount = part.ayahCount,
                        repeatCount = repeat,
                        lesson = part,
                    )
                }
            PartKind.Recent -> assignment.recent?.let { revisionPractice(state, kind, it.pages, repeat) }
            PartKind.Old -> assignment.old?.let { revisionPractice(state, kind, it.pages, repeat) }
        }
    }

    private fun revisionPractice(
        state: HifzState,
        kind: PartKind,
        pages: List<Int>,
        repeat: Int,
    ): PracticeRequest {
        val first = state.table.ayahsOn(pages.first()).first()
        val ayahCount = pages.sumOf { state.table.ayahsStartingOn(it).size }
        return PracticeRequest(
            kind = kind,
            method = state.plan.method,
            pages = pages,
            firstAyah = first,
            lastAyah = state.table.ayahsOn(pages.last()).last(),
            ayahCount = ayahCount,
            repeatCount = repeat,
            lesson = null,
        )
    }
}
