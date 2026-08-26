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

    /**
     * A "v2" stand-in with just the one table MIGRATION_2_3 actually touches (`exercises`) --
     * enough columns to insert a realistic row and prove the migration is additive, not the full
     * 11-table schema, matching [openV1]'s same "sufficient for what this migration does" scope.
     */
    private fun openV2(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `exercises` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `exercise_type` TEXT NOT NULL,
                        `primary_muscle_group` TEXT NOT NULL, `is_custom` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
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

    /** A "v3" stand-in: `exercises` including the `primary_muscle_head` column MIGRATION_3_4 reads from. */
    private fun openV3(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `exercises` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `exercise_type` TEXT NOT NULL,
                        `primary_muscle_group` TEXT NOT NULL, `is_custom` INTEGER NOT NULL,
                        `primary_muscle_head` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun `MIGRATION_2_3 adds a nullable primary_muscle_head column to exercises`() {
        val helper = openV2()
        val db = helper.writableDatabase
        LogEzDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("PRAGMA table_info(`exercises`)")
        var found = false
        var type = ""
        var notNull = true
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            val typeIdx = it.getColumnIndexOrThrow("type")
            val notNullIdx = it.getColumnIndexOrThrow("notnull")
            while (it.moveToNext()) {
                if (it.getString(nameIdx) == "primary_muscle_head") {
                    found = true
                    type = it.getString(typeIdx)
                    notNull = it.getInt(notNullIdx) == 1
                }
            }
        }
        db.close()

        assertTrue("expected a primary_muscle_head column", found)
        assertEquals("TEXT", type)
        assertTrue("column must be nullable so every pre-existing row defaults to unspecified", !notNull)
    }

    @Test
    fun `MIGRATION_2_3 leaves pre-existing exercise rows intact, with a null head`() {
        val helper = openV2()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO exercises (id, name, exercise_type, primary_muscle_group, is_custom) VALUES ('ex-1', 'Bench Press', 'WEIGHT_REPS', 'CHEST', 0)",
        )

        LogEzDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT name, primary_muscle_head FROM exercises WHERE id = 'ex-1'")
        cursor.moveToFirst()
        val name = cursor.getString(0)
        val headIsNull = cursor.isNull(1)
        cursor.close()
        db.close()

        assertEquals("Bench Press", name)
        assertTrue("a pre-existing row's head must default to null (unspecified), not be dropped", headIsNull)
    }

    @Test
    fun `MIGRATION_3_4 adds a non-null muscle_heads column defaulting to an empty JSON list`() {
        val helper = openV3()
        val db = helper.writableDatabase
        LogEzDatabase.MIGRATION_3_4.migrate(db)

        val cursor = db.query("PRAGMA table_info(`exercises`)")
        var found = false
        var type = ""
        var notNull = false
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            val typeIdx = it.getColumnIndexOrThrow("type")
            val notNullIdx = it.getColumnIndexOrThrow("notnull")
            while (it.moveToNext()) {
                if (it.getString(nameIdx) == "muscle_heads") {
                    found = true
                    type = it.getString(typeIdx)
                    notNull = it.getInt(notNullIdx) == 1
                }
            }
        }
        db.close()

        assertTrue("expected a muscle_heads column", found)
        assertEquals("TEXT", type)
        assertTrue("column must be NOT NULL -- an empty list, not a null, represents 'no heads picked'", notNull)
    }

    @Test
    fun `MIGRATION_3_4 carries an existing single primary_muscle_head forward as a one-element list`() {
        val helper = openV3()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO exercises (id, name, exercise_type, primary_muscle_group, is_custom, primary_muscle_head) " +
                "VALUES ('ex-1', 'Lateral Raise', 'WEIGHT_REPS', 'SHOULDERS', 0, 'LATERAL_DELTOID')",
        )

        LogEzDatabase.MIGRATION_3_4.migrate(db)

        val cursor = db.query("SELECT muscle_heads FROM exercises WHERE id = 'ex-1'")
        cursor.moveToFirst()
        val muscleHeads = cursor.getString(0)
        cursor.close()
        db.close()

        assertEquals("""["LATERAL_DELTOID"]""", muscleHeads)
    }

    @Test
    fun `MIGRATION_3_4 defaults a row with no primary_muscle_head to an empty list`() {
        val helper = openV3()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO exercises (id, name, exercise_type, primary_muscle_group, is_custom, primary_muscle_head) " +
                "VALUES ('ex-2', 'Plank', 'DURATION', 'ABDOMINALS', 0, NULL)",
        )

        LogEzDatabase.MIGRATION_3_4.migrate(db)

        val cursor = db.query("SELECT muscle_heads FROM exercises WHERE id = 'ex-2'")
        cursor.moveToFirst()
        val muscleHeads = cursor.getString(0)
        cursor.close()
        db.close()

        assertEquals("[]", muscleHeads)
    }
}
