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
}
