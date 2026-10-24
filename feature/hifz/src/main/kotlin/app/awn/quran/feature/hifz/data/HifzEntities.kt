package app.awn.quran.feature.hifz.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * The one plan of the user; the table holds at most one row, with [SINGLE_ROW_ID]. Text values
 * ([method], [lessonKind], [printLayout]) are stored in the database, so they never change.
 * [cursorSurah] and [cursorAyah] are the first ayah of the next new lesson, both null while the
 * lessons start from the first page that is not known yet. Days are epoch days.
 */
@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val method: String,
    @ColumnInfo(name = "lesson_kind") val lessonKind: String,
    @ColumnInfo(name = "lesson_ayahs") val lessonAyahs: Int,
    @ColumnInfo(name = "manzil_cycle_days") val manzilCycleDays: Int,
    @ColumnInfo(name = "recent_days") val recentDays: Int,
    @ColumnInfo(name = "ayahs_per_day") val ayahsPerDay: Int,
    @ColumnInfo(name = "revision_page_limit") val revisionPageLimit: Int,
    @ColumnInfo(name = "cursor_surah") val cursorSurah: Int?,
    @ColumnInfo(name = "cursor_ayah") val cursorAyah: Int?,
    /** Which pagination the page numbers of this database follow; see `PageLayoutFamily`. */
    @ColumnInfo(name = "print_layout") val printLayout: String,
    @ColumnInfo(name = "started_on") val startedOn: Long,
    /**
     * `RevisionSpacingName`; null keeps the method's own spacing (plans saved before version 2).
     */
    @ColumnInfo(name = "revision_spacing") val revisionSpacing: String? = null,
    /** The fixed steps in days, comma separated; null uses the default steps. */
    @ColumnInfo(name = "spacing_steps") val spacingSteps: String? = null,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

/**
 * What the user knows of one page. A page without a row is not started. [status] holds
 * `PageStatus.value`. The memory state columns are null until the page has had its first graded
 * review; [state] holds `FsrsState.value` and [step] the learning step, as in the learn database.
 */
@Entity(tableName = "page_progress")
data class PageProgressEntity(
    @PrimaryKey val page: Int,
    val status: Int,
    /** Epoch day of the last lesson that taught part of this page; the recent window reads it. */
    @ColumnInfo(name = "learned_on") val learnedOn: Long?,
    @ColumnInfo(name = "last_revised_on") val lastRevisedOn: Long?,
    val stability: Double?,
    val difficulty: Double?,
    /** Epoch milliseconds. */
    val due: Long?,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "last_review") val lastReview: Long?,
    val state: Int,
    @ColumnInfo(defaultValue = "0") val step: Int = 0,
    /** The page's place in the fixed spacing steps. */
    @ColumnInfo(name = "spacing_step", defaultValue = "0") val spacingStep: Int = 0,
)

/** What a finished part of a day was; the text values are stored, so they never change. */
object SessionKind {
    const val NEW_LESSON = "new_lesson"
    const val RECENT = "recent"
    const val OLD = "old"
}

/**
 * One saved part of a day. [pages] lists the pages it was given, comma separated. The ayah columns
 * are set for a new lesson and null for revision. [grade] is the name of the weakest grade the user
 * gave. A part saved before its end is not [isComplete]; parts saved before version 2 always were.
 * The answers for each ayah and page are in [SessionAyahEntity] and [SessionPageEntity].
 */
@Entity(tableName = "session", indices = [Index("day")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day. */
    val day: Long,
    val kind: String,
    val pages: String,
    @ColumnInfo(name = "first_surah") val firstSurah: Int?,
    @ColumnInfo(name = "first_ayah") val firstAyah: Int?,
    @ColumnInfo(name = "last_surah") val lastSurah: Int?,
    @ColumnInfo(name = "last_ayah") val lastAyah: Int?,
    val grade: String,
    /** Epoch milliseconds. */
    val at: Long,
    @ColumnInfo(name = "is_complete", defaultValue = "1") val isComplete: Boolean = true,
    /** How long the practice lasted; null for parts saved before version 2. */
    @ColumnInfo(name = "duration_millis") val durationMillis: Long? = null,
)

/**
 * What the user said of one ayah of a saved new lesson; [firmness] is `AyahFirmness.storedName`.
 */
@Entity(tableName = "session_ayah", primaryKeys = ["session_id", "surah", "ayah"])
data class SessionAyahEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val surah: Int,
    val ayah: Int,
    val firmness: String,
)

/** The grade of one page of a saved revision; a page the user did not reach has no row. */
@Entity(tableName = "session_page", primaryKeys = ["session_id", "page"])
data class SessionPageEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val page: Int,
    val grade: String,
)

/** A saved part with its answers, read in one transaction. */
data class SessionWithResults(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id") val ayahs: List<SessionAyahEntity>,
    @Relation(parentColumn = "id", entityColumn = "session_id") val pages: List<SessionPageEntity>,
)

/** What went wrong in an ayah; the text values are stored, so they never change. */
object MistakeKind {
    const val SKIPPED = "skipped"
    const val OTHER_WORD = "other_word"
}

/** One mistake found while the user recited an ayah during practice. */
@Entity(tableName = "mistake", indices = [Index("surah", "ayah"), Index("at")])
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val kind: String,
    /** Epoch milliseconds. */
    val at: Long,
)
