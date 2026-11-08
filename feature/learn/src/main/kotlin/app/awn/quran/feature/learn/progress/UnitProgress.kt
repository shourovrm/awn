package app.awn.quran.feature.learn.progress

import app.awn.quran.feature.learn.data.UnitProgressDao
import app.awn.quran.feature.learn.data.UnitProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where the user is in one unit: [page] is the page on screen, [furthestPage] the furthest page
 * reached. Pages count from 0; [updatedAt] is epoch milliseconds.
 */
data class UnitProgress(
    val lessonId: String,
    val page: Int,
    val furthestPage: Int,
    val updatedAt: Long,
)

/** Remembers the page of each unit. The clock is a parameter so tests can fix the time. */
class UnitProgressStore(private val dao: UnitProgressDao, private val clock: () -> Long) {

    /** The page a unit opens on; 0 for a unit never opened. */
    suspend fun pageOf(lessonId: String): Int = dao.find(lessonId)?.page ?: 0

    suspend fun save(lessonId: String, page: Int) {
        write(lessonId, page, maxOf(furthestOf(lessonId), page))
    }

    /** A finished unit opens on its first page next time; how far it went is kept. */
    suspend fun finish(lessonId: String) {
        write(lessonId, 0, furthestOf(lessonId))
    }

    suspend fun restart(lessonId: String) {
        write(lessonId, 0, 0)
    }

    fun all(): Flow<List<UnitProgress>> =
        dao.observeAll().map { rows ->
            rows.map { UnitProgress(it.lessonId, it.page, it.furthestPage, it.updatedAt) }
        }

    private suspend fun furthestOf(lessonId: String): Int = dao.find(lessonId)?.furthestPage ?: 0

    private suspend fun write(lessonId: String, page: Int, furthestPage: Int) {
        dao.upsert(UnitProgressEntity(lessonId, page, furthestPage, clock()))
    }
}

/** The page to open a unit on: the saved page, moved back into the pages the unit has now. */
fun resumePage(saved: Int, pageCount: Int): Int =
    if (pageCount == 0) 0 else saved.coerceIn(0, pageCount - 1)

/** The unit the user left in the middle most recently, or null when none is in the middle. */
fun continueUnit(progress: List<UnitProgress>): UnitProgress? =
    progress.filter { it.page > 0 }.maxByOrNull { it.updatedAt }
