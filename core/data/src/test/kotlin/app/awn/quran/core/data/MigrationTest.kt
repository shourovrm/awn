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

    @Test
    fun versionOneBookmarksSurviveTheMigrationToVersionTwo() {
        val databaseName = "migration-keeps-data"
        migrationHelper.createDatabase(databaseName, 1).use { oldDatabase ->
            oldDatabase.execSQL(
                "INSERT INTO bookmark (surah, ayah, wordPosition, createdAtEpochSeconds) " +
                    "VALUES (2, 255, 3, 1700000000)",
            )
        }

        migrationHelper
            .runMigrationsAndValidate(databaseName, 2, true, *AwnMigrations.all.toTypedArray())
            .use { migrated ->
                migrated.query("SELECT surah, ayah, wordPosition FROM bookmark").use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals(2, cursor.getInt(0))
                    assertEquals(255, cursor.getInt(1))
                    assertEquals(3, cursor.getInt(2))
                }
                migrated.execSQL("INSERT INTO bookmarked_page (page, createdAt) VALUES (1, 1)")
                migrated.execSQL(
                    "INSERT INTO saved_ayah (surah, ayah, note, createdAt) VALUES (1, 1, NULL, 1)",
                )
                migrated.execSQL("INSERT INTO recent_page (page, openedAt) VALUES (1, 1)")
            }
    }
}
