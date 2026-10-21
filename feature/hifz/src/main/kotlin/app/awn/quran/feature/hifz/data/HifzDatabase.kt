package app.awn.quran.feature.hifz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities =
        [
            PlanEntity::class,
            PageProgressEntity::class,
            SessionEntity::class,
            MistakeEntity::class,
        ],
    version = HifzDatabase.CURRENT_VERSION,
    exportSchema = true,
)
abstract class HifzDatabase : RoomDatabase() {

    abstract fun dao(): HifzDao

    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "hifz.db"

        /**
         * Every schema change, oldest first. A migration moves forward by exactly one version and
         * only adds to the schema, so a database written by an older app opens in a newer one.
         */
        val migrations: List<Migration> = emptyList()

        /**
         * Opens the memorisation database. There is deliberately no destructive-migration fallback:
         * a user's map of memorised pages is never dropped, so a missing migration fails in tests,
         * not in the field.
         */
        // Room takes a vararg; this runs once at startup, so the array copy is irrelevant.
        @Suppress("SpreadOperator")
        fun open(context: Context): HifzDatabase =
            Room.databaseBuilder(context, HifzDatabase::class.java, FILE_NAME)
                .addMigrations(*migrations.toTypedArray())
                .build()
    }
}
