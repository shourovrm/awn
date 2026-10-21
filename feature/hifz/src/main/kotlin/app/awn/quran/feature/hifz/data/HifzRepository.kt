package app.awn.quran.feature.hifz.data

import androidx.room.withTransaction
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzPlan
import app.awn.quran.feature.hifz.method.LessonPart
import app.awn.quran.feature.hifz.method.MistakeRecord
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

    /** Sets what the user says they know about [pages]; Not started removes the rows. */
    suspend fun markPages(pages: IntRange, status: PageStatus) {
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            if (status == PageStatus.NotStarted) {
                dao.deletePages(pages.toList())
            } else {
                dao.upsertPages(
                    pages.map { PageProgressRules.markPage(it, status, day, nowMillis).toEntity() },
                )
            }
        }
    }

    /** Records a graded new lesson: its pages, the plan's cursor and the log. */
    suspend fun finishLesson(table: PageTable, part: LessonPart, grade: PartGrade) {
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            val plan = checkNotNull(dao.findPlan()) { "A lesson was finished without a plan." }
            val outcome =
                PageProgressRules.completeLesson(
                    table = table,
                    part = part,
                    grade = grade,
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
            dao.insertSession(
                SessionEntity(
                    day = day.toEpochDay(),
                    kind = PartKind.NewLesson.storedName(),
                    pages = (part.firstPage..part.lastPage).joinToString(","),
                    firstSurah = part.first.surah,
                    firstAyah = part.first.ayah,
                    lastSurah = part.last.surah,
                    lastAyah = part.last.ayah,
                    grade = grade.name,
                    at = nowMillis,
                ),
            )
        }
    }

    /** Records a graded revision of [pages], one grade for the whole group. */
    suspend fun finishRevision(kind: PartKind, pages: List<Int>, grade: PartGrade) {
        require(kind != PartKind.NewLesson) { "A new lesson is finished with finishLesson." }
        val nowMillis = clock()
        val day = today()
        database.withTransaction {
            val current = records()
            val updated = pages.map { page ->
                val record = current[page] ?: PageRecord(page, PageStatus.NotStarted)
                PageProgressRules.gradeRevision(record, grade, day, nowMillis)
            }
            dao.upsertPages(updated.map { it.toEntity() })
            dao.insertSession(
                SessionEntity(
                    day = day.toEpochDay(),
                    kind = kind.storedName(),
                    pages = pages.joinToString(","),
                    firstSurah = null,
                    firstAyah = null,
                    lastSurah = null,
                    lastAyah = null,
                    grade = grade.name,
                    at = nowMillis,
                ),
            )
        }
    }

    suspend fun recordMistakes(mistakes: List<MistakeRecord>) {
        if (mistakes.isEmpty()) return
        dao.insertMistakes(mistakes.map { it.toEntity() })
    }
}
