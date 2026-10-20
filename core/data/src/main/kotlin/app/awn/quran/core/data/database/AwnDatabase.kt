package app.awn.quran.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookmarkEntity::class],
    version = AwnDatabase.CURRENT_VERSION,
    exportSchema = true,
)
abstract class AwnDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "awn.db"

        /**
         * Opens the user database. There is deliberately no destructive-migration fallback: user
         * data is never dropped, so a missing migration crashes loudly in tests, not in the field.
         */
        // Room takes a vararg; this runs once at startup, so the array copy is irrelevant.
        @Suppress("SpreadOperator")
        fun open(context: Context): AwnDatabase =
            Room.databaseBuilder(context, AwnDatabase::class.java, FILE_NAME)
                .addMigrations(*AwnMigrations.all.toTypedArray())
                .build()
    }
}
