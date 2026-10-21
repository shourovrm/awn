package app.awn.quran.feature.hifz

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.awn.quran.feature.hifz.data.HifzDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Add a `Migration` to [HifzDatabase.migrations] when the schema version goes up; this class then
 * checks, without edits, that every older exported schema migrates to the current one.
 */
@RunWith(RobolectricTestRunner::class)
class HifzMigrationTest {

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            HifzDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrationsFormAForwardOnlyChainToTheCurrentVersion() {
        val steps = HifzDatabase.migrations.sortedBy { it.startVersion }

        steps.forEach { assertEquals(it.startVersion + 1, it.endVersion) }
        assertEquals(
            (1 until HifzDatabase.CURRENT_VERSION).toList(),
            steps.map { it.startVersion },
        )
    }

    @Test
    fun everyOlderSchemaMigratesToTheCurrentVersion() {
        for (oldVersion in 1..HifzDatabase.CURRENT_VERSION) {
            val databaseName = "migration-from-$oldVersion"
            migrationHelper.createDatabase(databaseName, oldVersion).close()

            migrationHelper
                .runMigrationsAndValidate(
                    databaseName,
                    HifzDatabase.CURRENT_VERSION,
                    true,
                    *HifzDatabase.migrations.toTypedArray(),
                )
                .close()
        }
    }
}
