package app.awn.quran.feature.learn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            EventEntity::class,
            CardEntity::class,
            WordCardEntity::class,
            GrammarCardEntity::class,
            QueuedWordEntity::class,
            LessonDoneEntity::class,
            LearnSetEntity::class,
            LearnStepEntity::class,
            SessionLogEntity::class,
            UnitProgressEntity::class,
        ],
    version = LearnDatabase.CURRENT_VERSION,
    exportSchema = true,
)
abstract class LearnDatabase : RoomDatabase() {

    abstract fun dao(): LearnDao

    abstract fun wordDao(): WordDao

    abstract fun unitProgressDao(): UnitProgressDao

    companion object {
        const val CURRENT_VERSION = 3
        const val FILE_NAME = "learn.db"

        /** Version 2 adds the tables of the word course; the ayah tables are untouched. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    WORD_COURSE_TABLES.forEach(db::execSQL)
                }
            }

        /** Version 3 adds the page each unit was left on; no other table changes. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(UNIT_PROGRESS_TABLE)
                }
            }

        /**
         * Every schema change, oldest first. A migration moves forward by exactly one version and
         * only adds to the schema, so a database written by an older app opens in a newer one.
         */
        val migrations: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)

        /**
         * Opens the learning database. There is deliberately no destructive-migration fallback:
         * progress is never dropped, so a missing migration fails in tests, not in the field.
         */
        // Room takes a vararg; this runs once at startup, so the array copy is irrelevant.
        @Suppress("SpreadOperator")
        fun open(context: Context): LearnDatabase =
            Room.databaseBuilder(context, LearnDatabase::class.java, FILE_NAME)
                .addMigrations(*migrations.toTypedArray())
                .build()

        // The statement equals the createSql of schemas/.../3.json; the migration test runs it
        // and lets Room compare the result with that schema.
        private const val UNIT_PROGRESS_TABLE =
            "CREATE TABLE IF NOT EXISTS `unit_progress` (`lesson_id` TEXT NOT NULL, " +
                "`page` INTEGER NOT NULL, `furthest_page` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`lesson_id`))"

        // The statements below equal the createSql of schemas/.../2.json; the migration test opens
        // a version 1 file, runs them and lets Room compare the result with that schema.
        private val WORD_COURSE_TABLES =
            listOf(
                "CREATE TABLE IF NOT EXISTS `word_card` (`rank` INTEGER NOT NULL, " +
                    "`stability` REAL, `difficulty` REAL, `due` INTEGER NOT NULL, " +
                    "`last_review` INTEGER, `reps` INTEGER NOT NULL, `lapses` INTEGER NOT NULL, " +
                    "`state` INTEGER NOT NULL, `step` INTEGER NOT NULL, " +
                    "`met_at` INTEGER NOT NULL, `last_question_type` TEXT, PRIMARY KEY(`rank`))",
                "CREATE TABLE IF NOT EXISTS `grammar_card` (`lesson_id` TEXT NOT NULL, " +
                    "`stability` REAL NOT NULL, `difficulty` REAL NOT NULL, " +
                    "`due` INTEGER NOT NULL, `last_review` INTEGER NOT NULL, " +
                    "`reps` INTEGER NOT NULL, `lapses` INTEGER NOT NULL, " +
                    "`state` INTEGER NOT NULL, `step` INTEGER NOT NULL, PRIMARY KEY(`lesson_id`))",
                "CREATE TABLE IF NOT EXISTS `queued_word` (`rank` INTEGER NOT NULL, " +
                    "`added_at` INTEGER NOT NULL, PRIMARY KEY(`rank`))",
                "CREATE TABLE IF NOT EXISTS `lesson_done` (`lesson_id` TEXT NOT NULL, " +
                    "`done_at` INTEGER NOT NULL, PRIMARY KEY(`lesson_id`))",
                "CREATE TABLE IF NOT EXISTS `learn_set` (`number` INTEGER NOT NULL, " +
                    "`started_at` INTEGER NOT NULL, `finished_at` INTEGER, PRIMARY KEY(`number`))",
                "CREATE TABLE IF NOT EXISTS `learn_step` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                    "NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`item_kind` TEXT NOT NULL, `item` TEXT NOT NULL, `question_type` TEXT, " +
                    "`outcome` TEXT NOT NULL, `millis` INTEGER NOT NULL, " +
                    "`is_own_quiz` INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_learn_step_at` ON `learn_step` (`at`)",
                "CREATE TABLE IF NOT EXISTS `session_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                    "NOT NULL, `started_at` INTEGER NOT NULL, `planned_seconds` INTEGER NOT NULL, " +
                    "`elapsed_millis` INTEGER NOT NULL, `reviews` INTEGER NOT NULL, " +
                    "`new_words` INTEGER NOT NULL, `lessons` INTEGER NOT NULL, " +
                    "`words_known_before` INTEGER NOT NULL, `words_met_before` INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_session_log_started_at` ON `session_log` " +
                    "(`started_at`)",
            )
    }
}
