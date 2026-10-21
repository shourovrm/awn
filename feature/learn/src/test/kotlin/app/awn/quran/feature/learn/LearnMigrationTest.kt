package app.awn.quran.feature.learn

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.awn.quran.feature.learn.data.LearnDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Add a `Migration` to [LearnDatabase.migrations] when the schema version goes up; this class then
 * checks, without edits, that every older exported schema migrates to the current one.
 */
@RunWith(RobolectricTestRunner::class)
class LearnMigrationTest {

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LearnDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrationsFormAForwardOnlyChainToTheCurrentVersion() {
        val steps = LearnDatabase.migrations.sortedBy { it.startVersion }

        steps.forEach { assertEquals(it.startVersion + 1, it.endVersion) }
        assertEquals(
            (1 until LearnDatabase.CURRENT_VERSION).toList(),
            steps.map { it.startVersion },
        )
    }

    @Test
    fun everyOlderSchemaMigratesToTheCurrentVersion() {
        for (oldVersion in 1..LearnDatabase.CURRENT_VERSION) {
            val databaseName = "migration-from-$oldVersion"
            migrationHelper.createDatabase(databaseName, oldVersion).close()

            migrationHelper
                .runMigrationsAndValidate(
                    databaseName,
                    LearnDatabase.CURRENT_VERSION,
                    true,
                    *LearnDatabase.migrations.toTypedArray(),
                )
                .close()
        }
    }

    @Test
    fun versionTwoKeepsTheAyahCardsAndAddsEmptyWordTables() {
        val databaseName = "migration-keeps-cards"
        migrationHelper
            .createDatabase(databaseName, 1)
            .apply {
                execSQL(
                    "INSERT INTO card (surah, ayah, stability, difficulty, due, last_review, " +
                        "reps, lapses, state, step) VALUES (2, 255, 3.5, 5.0, 1000, 500, 4, 1, 2, 0)",
                )
                close()
            }

        val migrated =
            migrationHelper.runMigrationsAndValidate(
                databaseName,
                2,
                true,
                *LearnDatabase.migrations.toTypedArray(),
            )

        migrated.query("SELECT reps FROM card WHERE surah = 2 AND ayah = 255").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(4, it.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM word_card").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }
}
