package com.enil.logez.core.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M8d — the project's first real schema migration (project-rules.md: `fallbackToDestructiveMigration`
 * is never used; every schema change ships a tested [androidx.room.migration.Migration]).
 *
 * `MigrationTestHelper`'s schema-file-driven validation needs `schemas/` exposed as a Robolectric
 * test asset; on this project's AGP 9.2.0 + Robolectric combination the unit-test asset merge
 * Robolectric reads (`test_config.properties` -> `android_merged_assets`) only ever points at the
 * main `debug` variant's merged assets, never a unit-test-specific merge, so that file is
 * unreachable from `app/src/test` regardless of which Gradle source set declares it. Rather than
 * fight the toolchain, this runs the actual production [LogEzDatabase.MIGRATION_1_2] object
 * against a real (Robolectric-backed) SQLite connection via the same `SupportSQLiteOpenHelper`
 * plumbing Room itself uses, and inspects the resulting schema directly — a smaller test, but one
 * that still exercises the literal code path production runs, not a hand-copied stand-in for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogEzDatabaseMigrationTest {
    /** An empty "v1" database — sufficient here because MIGRATION_1_2 only adds a new, independent table. */
    private fun openV1(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun `MIGRATION_1_2 creates goal_definitions with the entity's exact columns`() {
        val helper = openV1()
        val db = helper.writableDatabase // triggers onCreate at v1
        LogEzDatabase.MIGRATION_1_2.migrate(db)

        val cursor = db.query("PRAGMA table_info(`goal_definitions`)")
        val columns = mutableMapOf<String, Pair<String, Boolean>>() // name -> (type, notNull)
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            val typeIdx = it.getColumnIndexOrThrow("type")
            val notNullIdx = it.getColumnIndexOrThrow("notnull")
            while (it.moveToNext()) {
                columns[it.getString(nameIdx)] = it.getString(typeIdx) to (it.getInt(notNullIdx) == 1)
            }
        }

        assertEquals(setOf("id", "metric", "period", "target_value", "created_at", "updated_at"), columns.keys)
        assertEquals("TEXT" to true, columns["id"])
        assertEquals("TEXT" to true, columns["metric"])
        assertEquals("TEXT" to true, columns["period"])
        assertEquals("REAL" to true, columns["target_value"])
        assertEquals("INTEGER" to true, columns["created_at"])
        assertEquals("INTEGER" to true, columns["updated_at"])

        db.close()
    }

    @Test
    fun `MIGRATION_1_2 can read back a row it just accepted, and is safe to run twice`() {
        val helper = openV1()
        val db = helper.writableDatabase
        LogEzDatabase.MIGRATION_1_2.migrate(db)
        LogEzDatabase.MIGRATION_1_2.migrate(db) // CREATE TABLE IF NOT EXISTS must not throw on a second run

        db.execSQL(
            "INSERT INTO goal_definitions (id, metric, period, target_value, created_at, updated_at) VALUES ('g1', 'VOLUME', 'WEEKLY', 10000.0, 0, 0)",
        )
        val cursor = db.query("SELECT COUNT(*) FROM goal_definitions")
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        db.close()

        assertTrue(count == 1)
    }
}
