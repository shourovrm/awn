package app.awn.quran.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A bookmarked word; the three key columns mirror `WordKey`. */
@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val wordPosition: Int,
    val createdAtEpochSeconds: Long,
)
