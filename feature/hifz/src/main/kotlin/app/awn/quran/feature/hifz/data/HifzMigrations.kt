package app.awn.quran.feature.hifz.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The schema changes of the memorisation database, one object per version step. */
internal object HifzMigrations {

    /**
     * Version 2: the answers of each ayah and page, the practice time, parts saved before their
     * end, and the revision spacing. Old sessions keep their meaning: each was a whole part.
     */
    val ONE_TO_TWO =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan ADD COLUMN revision_spacing TEXT")
                db.execSQL("ALTER TABLE plan ADD COLUMN spacing_steps TEXT")
                db.execSQL(
                    "ALTER TABLE page_progress ADD COLUMN spacing_step INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE session ADD COLUMN is_complete INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE session ADD COLUMN duration_millis INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_ayah (session_id INTEGER NOT NULL, " +
                        "surah INTEGER NOT NULL, ayah INTEGER NOT NULL, firmness TEXT NOT NULL, " +
                        "PRIMARY KEY(session_id, surah, ayah))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_page (session_id INTEGER NOT NULL, " +
                        "page INTEGER NOT NULL, grade TEXT NOT NULL, PRIMARY KEY(session_id, page))",
                )
            }
        }
}
