package app.awn.quran.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change, oldest first. Migrations only move forward by exactly one version and only
 * add to the schema (new tables, new nullable or defaulted columns), so a database written by an
 * older app version opens in a newer one and the data release stays compatible.
 */
object AwnMigrations {

    /**
     * Adds page bookmarks, saved ayahs and recent pages. The word-based `bookmark` table from
     * version 1 stays as it is: no released app wrote to it and dropping tables is not allowed.
     */
    val addPageBookmarksSavedAyahsAndRecentPages =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarked_page` " +
                        "(`page` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`page`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_ayah` " +
                        "(`surah` INTEGER NOT NULL, `ayah` INTEGER NOT NULL, `note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`surah`, `ayah`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_page` " +
                        "(`page` INTEGER NOT NULL, `openedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`page`))",
                )
            }
        }

    val all: List<Migration> = listOf(addPageBookmarksSavedAyahsAndRecentPages)
}
