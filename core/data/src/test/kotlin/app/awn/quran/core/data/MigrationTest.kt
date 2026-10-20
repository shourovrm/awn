package app.awn.quran.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.awn.quran.core.data.database.AwnDatabase
import app.awn.quran.core.data.database.AwnMigrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration harness. Add a `Migration` to [AwnMigrations] when the schema version goes up and this
 * class checks, without further edits, that every older exported schema still migrates to the
 * current one and passes Room's schema validation.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AwnDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrationsFormAForwardOnlyChainToTheCurrentVersion() {
        val steps = AwnMigrations.all.sortedBy { it.startVersion }

        steps.forEach { assertEquals(it.startVersion + 1, it.endVersion) }
        assertEquals((1 until AwnDatabase.CURRENT_VERSION).toList(), steps.map { it.startVersion })
    }

    @Test
    fun everyOlderSchemaMigratesToTheCurrentVersion() {
        for (oldVersion in 1..AwnDatabase.CURRENT_VERSION) {
            val databaseName = "migration-from-$oldVersion"
            migrationHelper.createDatabase(databaseName, oldVersion).close()

            migrationHelper
                .runMigrationsAndValidate(
                    databaseName,
                    AwnDatabase.CURRENT_VERSION,
                    true,
                    *AwnMigrations.all.toTypedArray(),
                )
                .close()
        }
    }
}
