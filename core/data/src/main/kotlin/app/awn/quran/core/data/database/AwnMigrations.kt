package app.awn.quran.core.data.database

import androidx.room.migration.Migration

/**
 * Every schema change, oldest first. Migrations only move forward by exactly one version and only
 * add to the schema (new tables, new nullable or defaulted columns), so a database written by an
 * older app version opens in a newer one and the data release stays compatible.
 */
object AwnMigrations {
    val all: List<Migration> = emptyList()
}
