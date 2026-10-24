package app.awn.quran.feature.hifz.data

import androidx.room.withTransaction
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzPlan
import app.awn.quran.feature.hifz.method.JournalEntry
import app.awn.quran.feature.hifz.method.LessonPart
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.PageMarking
import app.awn.quran.feature.hifz.method.PageProgressRules
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTable
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads and writes the hifz database in the terms of the method code. Every write that touches
 * several rows is one transaction, so a crash never leaves a lesson half recorded.
 */
// One function per thing the screens read or record.
@Suppress("TooManyFunctions")
class HifzRepository(
    private val database: HifzDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val dao = database.dao()

    fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    fun observePlan(): Flow<HifzPlan?> = dao.observePlan().map { it?.toPlan() }

    fun observeRecords(): Flow<Map<Int, PageRecord>> =
        dao.observePages().map { rows -> rows.map { it.toRecord() }.associateBy { it.page } }

    fun observeFinishedOn(day: LocalDate): Flow<List<FinishedPart>> =
        dao.observeSessionsOn(day.toEpochDay()).map { rows -> rows.map { it.toFinishedPart() } }

    /** Every saved part from [firstDay] on, newest first. */
    fun observeJournalFrom(firstDay: LocalDate): Flow<List<JournalEntry>> =
        dao.observeSessionsFrom(firstDay.toEpochDay()).map { rows ->
            rows.map { it.toJournalEntry() }
        }

    /** What the user last said of each ayah they saved a lesson answer for. */
    fun observeLatestAnswers(): Flow<Map<AyahRef, AyahFirmness>> =
        dao.observeAyahAnswers().map { it.latestByAyah() }

    fun observeMistakesSince(sinceMillis: Long): Flow<List<MistakeRecord>> =
        dao.observeMistakesSince(sinceMillis).map { rows -> rows.map { it.toRecord() } }

    suspend fun plan(): HifzPlan? = dao.findPlan()?.toPlan()

    suspend fun records(): Map<Int, PageRecord> =
        dao.allPages().map { it.toRecord() }.associateBy { it.page }

    suspend fun savePlan(plan: HifzPlan) = dao.upsertPlan(plan.toEntity())

    /** Replaces every page record, for the move to another print's pagination. */
    suspend fun replaceRecords(records: Map<Int, PageRecord>, plan: HifzPlan) {
        database.withTransaction {
            dao.deleteAllPages()
            dao.upsertPages(records.values.map { it.toEntity() })
            dao.upsertPlan(plan.toEntity())
        }
    }

    /**
     * Sets what the user says they know about [pages]; Not started removes the rows. A page that
     * stays known keeps its history, and newly known pages come back spread over up to
     * [PageMarking.SPREAD_DAYS] days.
     */
    suspend fun markPages(pages: IntRange, status: PageStatus) {
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            if (status == PageStatus.NotStarted) {
                dao.deletePages(pages.toList())
            } else {
                val existing = records()
                val pageList = pages.toList()
                dao.upsertPages(
                    pageList.mapIndexed { index, page ->
                        PageMarking.markPage(
                                page = page,
                                status = status,
                                today = day,
                                nowMillis = nowMillis,
                                existing = existing[page],
                                firstRevisionInDays = PageMarking.spreadDays(index, pageList.size),
                            )
                            .toEntity()
                    },
                )
            }
        }
    }

    /**
     * Sets how far the user has memorised: the plan's next lesson starts after [ayah], and every
     * page that ends at or before [ayah] becomes Memorised unless it already has a known status.
     * The page [ayah] falls inside stays open because the rest of it is still to learn. Without a
     * plan nothing is written.
     */
    suspend fun setKnownUpTo(table: PageTable, ayah: AyahRef) {
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            val plan = dao.findPlan() ?: return@withTransaction
            val knownPages = records().filterValues { it.status.isKnown }.keys
            val pagesBefore =
                (1..table.pageCount).filter { page ->
                    page !in knownPages && table.ayahsOn(page).all { it <= ayah }
                }
            dao.upsertPages(
                pagesBefore.mapIndexed { index, page ->
                    PageMarking.markPage(
                            page = page,
                            status = PageStatus.Memorised,
                            today = day,
                            nowMillis = nowMillis,
                            firstRevisionInDays = PageMarking.spreadDays(index, pagesBefore.size),
                        )
                        .toEntity()
                },
            )
            // After the last ayah there is nothing left to learn, so the cursor stays where it is.
            val next = table.ayahAfter(ayah) ?: return@withTransaction
            dao.upsertPlan(plan.copy(cursorSurah = next.surah, cursorAyah = next.ayah))
        }
    }

    /**
     * Records a saved new lesson: its pages, the plan's cursor, the log and what the user said of
     * each ayah. The session's grade is the weakest page grade, or Forgot when nothing was
     * memorised; [durationMillis] is how long the practice lasted.
     */
    suspend fun finishLesson(
        table: PageTable,
        part: LessonPart,
        firmness: Map<AyahRef, AyahFirmness>,
        durationMillis: Long?,
    ) {
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            val plan = checkNotNull(dao.findPlan()) { "A lesson was finished without a plan." }
            val outcome =
                PageProgressRules.completeLesson(
                    table = table,
                    part = part,
                    firmness = firmness,
                    today = day,
                    nowMillis = nowMillis,
                    records = records(),
                )
            dao.upsertPages(outcome.records.map { it.toEntity() })
            dao.upsertPlan(
                plan.copy(
                    cursorSurah = outcome.nextCursor?.surah,
                    cursorAyah = outcome.nextCursor?.ayah,
                ),
            )
            val weakest = firmness.values.filter { it.isMemorised }.minOrNull()
            val sessionId =
                dao.insertSession(
                    SessionEntity(
                        day = day.toEpochDay(),
                        kind = PartKind.NewLesson.storedName(),
                        pages = (part.firstPage..part.lastPage).joinToString(","),
                        firstSurah = part.first.surah,
                        firstAyah = part.first.ayah,
                        lastSurah = part.last.surah,
                        lastAyah = part.last.ayah,
                        grade = (weakest?.pageGrade ?: PartGrade.Forgot).name,
                        at = nowMillis,
                        isComplete = outcome.isComplete,
                        durationMillis = durationMillis,
                    ),
                )
            dao.insertSessionAyahs(
                firmness.map { (ayah, answer) ->
                    SessionAyahEntity(sessionId, ayah.surah, ayah.ayah, answer.storedName)
                },
            )
        }
    }

    /**
     * Records a saved revision of the [pages] the part was given. Only the pages in [grades] were
     * reached: they are graded one by one, the others stay on Today. Nothing is written when no
     * page was reached.
     */
    suspend fun finishRevision(
        kind: PartKind,
        pages: List<Int>,
        grades: Map<Int, PartGrade>,
        durationMillis: Long?,
    ) {
        require(kind != PartKind.NewLesson) { "A new lesson is finished with finishLesson." }
        if (grades.isEmpty()) return
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            val current = records()
            val updated = grades.map { (page, grade) ->
                val record = current[page] ?: PageRecord(page, PageStatus.NotStarted)
                PageProgressRules.gradeRevision(record, grade, day, nowMillis)
            }
            dao.upsertPages(updated.map { it.toEntity() })
            val donePages = finishedOn(day, kind).flatMap { it.gradedPages }.toSet() + grades.keys
            val sessionId =
                dao.insertSession(
                    SessionEntity(
                        day = day.toEpochDay(),
                        kind = kind.storedName(),
                        pages = pages.joinToString(","),
                        firstSurah = null,
                        firstAyah = null,
                        lastSurah = null,
                        lastAyah = null,
                        grade = grades.values.min().name,
                        at = nowMillis,
                        isComplete = donePages.containsAll(pages),
                        durationMillis = durationMillis,
                    ),
                )
            dao.insertSessionPages(
                grades.map { (page, grade) -> SessionPageEntity(sessionId, page, grade.name) },
            )
        }
    }

    private suspend fun finishedOn(day: LocalDate, kind: PartKind): List<FinishedPart> =
        dao.sessionsOn(day.toEpochDay()).map { it.toFinishedPart() }.filter { it.kind == kind }

    suspend fun recordMistakes(mistakes: List<MistakeRecord>) {
        if (mistakes.isEmpty()) return
        dao.insertMistakes(mistakes.map { it.toEntity() })
    }
}
