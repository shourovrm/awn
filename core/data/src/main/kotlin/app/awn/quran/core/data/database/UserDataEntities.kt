package app.awn.quran.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A bookmarked mushaf page (1..604). */
@Entity(tableName = "bookmarked_page")
data class BookmarkedPageEntity(@PrimaryKey val page: Int, val createdAt: Long)

/** A saved ayah; saving it again replaces the row. */
@Entity(tableName = "saved_ayah", primaryKeys = ["surah", "ayah"])
data class SavedAyahEntity(
    val surah: Int,
    val ayah: Int,
    val note: String?,
    val createdAt: Long,
)

/** The last time a page was opened; one row per page. */
@Entity(tableName = "recent_page")
data class RecentPageEntity(@PrimaryKey val page: Int, val openedAt: Long)
