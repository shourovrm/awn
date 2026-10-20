package app.awn.quran.feature.learn.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What happened. The text values are stored in the database, so they never change. */
object EventKind {
    const val PAGE_OPENED = "page_opened"
    const val AYAH_GREEN = "ayah_green"
    const val HIFZ_CLEAN = "hifz_clean"
    const val HIFZ_WITH_SKIPS = "hifz_with_skips"
    const val REVISION_RATED = "revision_rated"
}

/** One learning event. [surah], [ayah] and [page] are null when the kind does not carry them. */
@Entity(tableName = "event", indices = [Index("at")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val surah: Int?,
    val ayah: Int?,
    val page: Int?,
    /** Epoch milliseconds. */
    val at: Long,
)

/**
 * The FSRS memory state of one ayah. [state] holds `FsrsState.value`; [step] is the position in the
 * learning or relearning steps, which the FSRS scheduler needs between reviews.
 */
@Entity(tableName = "card", primaryKeys = ["surah", "ayah"])
data class CardEntity(
    val surah: Int,
    val ayah: Int,
    val stability: Double,
    val difficulty: Double,
    /** Epoch milliseconds. */
    val due: Long,
    @ColumnInfo(name = "last_review") val lastReview: Long,
    val reps: Int,
    val lapses: Int,
    val state: Int,
    @ColumnInfo(defaultValue = "0") val step: Int = 0,
)
