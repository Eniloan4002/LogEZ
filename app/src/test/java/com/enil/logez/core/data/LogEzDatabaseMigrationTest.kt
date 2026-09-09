package com.enil.logez.core.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val V3_SCHEMA_SQL = listOf(
    """CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `exercise_type` TEXT NOT NULL, `primary_muscle_group` TEXT NOT NULL, `secondary_muscle_groups` TEXT NOT NULL, `equipment` TEXT NOT NULL, `instructions` TEXT NOT NULL, `media_path` TEXT, `is_custom` INTEGER NOT NULL, `is_bodyweight_volume_eligible` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `primary_muscle_head` TEXT, PRIMARY KEY(`id`))""",
    """CREATE INDEX IF NOT EXISTS `index_exercises_primary_muscle_group` ON `exercises` (`primary_muscle_group`)""",
    """CREATE INDEX IF NOT EXISTS `index_exercises_equipment` ON `exercises` (`equipment`)""",
    """CREATE INDEX IF NOT EXISTS `index_exercises_is_deleted` ON `exercises` (`is_deleted`)""",
    """CREATE TABLE IF NOT EXISTS `routine_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `routines` (`id` TEXT NOT NULL, `folder_id` TEXT, `name` TEXT NOT NULL, `notes` TEXT, `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`folder_id`) REFERENCES `routine_folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
    """CREATE INDEX IF NOT EXISTS `index_routines_folder_id` ON `routines` (`folder_id`)""",
    """CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` TEXT NOT NULL, `routine_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `superset_group` INTEGER, `rest_timer_seconds` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
    """CREATE INDEX IF NOT EXISTS `index_routine_exercises_routine_id` ON `routine_exercises` (`routine_id`)""",
    """CREATE INDEX IF NOT EXISTS `index_routine_exercises_exercise_id` ON `routine_exercises` (`exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `routine_sets` (`id` TEXT NOT NULL, `routine_exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `set_type` TEXT NOT NULL, `target_weight_kg` REAL, `target_reps` INTEGER, `target_rep_range_min` INTEGER, `target_rep_range_max` INTEGER, `target_duration_seconds` INTEGER, `target_distance_meters` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`routine_exercise_id`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_routine_sets_routine_exercise_id` ON `routine_sets` (`routine_exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `workouts` (`id` TEXT NOT NULL, `routine_id` TEXT, `title` TEXT NOT NULL, `notes` TEXT, `status` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER, `duration_seconds` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
    """CREATE INDEX IF NOT EXISTS `index_workouts_routine_id` ON `workouts` (`routine_id`)""",
    """CREATE INDEX IF NOT EXISTS `index_workouts_status` ON `workouts` (`status`)""",
    """CREATE INDEX IF NOT EXISTS `index_workouts_started_at` ON `workouts` (`started_at`)""",
    """CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `superset_group` INTEGER, `rest_timer_seconds` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
    """CREATE INDEX IF NOT EXISTS `index_workout_exercises_workout_id` ON `workout_exercises` (`workout_id`)""",
    """CREATE INDEX IF NOT EXISTS `index_workout_exercises_exercise_id` ON `workout_exercises` (`exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `workout_sets` (`id` TEXT NOT NULL, `workout_exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `set_type` TEXT NOT NULL, `weight_kg` REAL, `reps` INTEGER, `duration_seconds` INTEGER, `distance_meters` REAL, `rpe` REAL, `custom_metric` REAL, `is_completed` INTEGER NOT NULL, `completed_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workout_exercise_id`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_workout_sets_workout_exercise_id` ON `workout_sets` (`workout_exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `personal_records` (`id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, `workout_set_id` TEXT, `pr_type` TEXT NOT NULL, `value` REAL NOT NULL, `achieved_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_records_exercise_id_pr_type` ON `personal_records` (`exercise_id`, `pr_type`)""",
    """CREATE INDEX IF NOT EXISTS `index_personal_records_workout_id` ON `personal_records` (`workout_id`)""",
    """CREATE TABLE IF NOT EXISTS `body_measurements` (`date` TEXT NOT NULL, `weight_kg` REAL, `lean_mass_kg` REAL, `fat_percent` REAL, `neck_cm` REAL, `shoulder_cm` REAL, `chest_cm` REAL, `left_bicep_cm` REAL, `right_bicep_cm` REAL, `left_forearm_cm` REAL, `right_forearm_cm` REAL, `abdomen_cm` REAL, `waist_cm` REAL, `hips_cm` REAL, `left_thigh_cm` REAL, `right_thigh_cm` REAL, `left_calf_cm` REAL, `right_calf_cm` REAL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`date`))""",
    """CREATE TABLE IF NOT EXISTS `progress_photos` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `file_path` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE INDEX IF NOT EXISTS `index_progress_photos_date` ON `progress_photos` (`date`)""",
    """CREATE TABLE IF NOT EXISTS `goal_definitions` (`id` TEXT NOT NULL, `metric` TEXT NOT NULL, `period` TEXT NOT NULL, `target_value` REAL NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
)

/** The real v4 schema: v3 plus `exercises.muscle_heads` (createSql taken verbatim from the exported `4.json`). */
private val V4_SCHEMA_SQL = V3_SCHEMA_SQL.map { sql ->
    if (sql.startsWith("CREATE TABLE IF NOT EXISTS `exercises`")) {
        """CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `exercise_type` TEXT NOT NULL, `primary_muscle_group` TEXT NOT NULL, `secondary_muscle_groups` TEXT NOT NULL, `equipment` TEXT NOT NULL, `instructions` TEXT NOT NULL, `media_path` TEXT, `is_custom` INTEGER NOT NULL, `is_bodyweight_volume_eligible` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `muscle_heads` TEXT NOT NULL DEFAULT '[]', `primary_muscle_head` TEXT, PRIMARY KEY(`id`))"""
    } else {
        sql
    }
}

private val V1_SCHEMA_SQL = listOf(
    """CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `exercise_type` TEXT NOT NULL, `primary_muscle_group` TEXT NOT NULL, `secondary_muscle_groups` TEXT NOT NULL, `equipment` TEXT NOT NULL, `instructions` TEXT NOT NULL, `media_path` TEXT, `is_custom` INTEGER NOT NULL, `is_bodyweight_volume_eligible` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE INDEX IF NOT EXISTS `index_exercises_primary_muscle_group` ON `exercises` (`primary_muscle_group`)""",
    """CREATE INDEX IF NOT EXISTS `index_exercises_equipment` ON `exercises` (`equipment`)""",
    """CREATE INDEX IF NOT EXISTS `index_exercises_is_deleted` ON `exercises` (`is_deleted`)""",
    """CREATE TABLE IF NOT EXISTS `routine_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE TABLE IF NOT EXISTS `routines` (`id` TEXT NOT NULL, `folder_id` TEXT, `name` TEXT NOT NULL, `notes` TEXT, `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`folder_id`) REFERENCES `routine_folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
    """CREATE INDEX IF NOT EXISTS `index_routines_folder_id` ON `routines` (`folder_id`)""",
    """CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` TEXT NOT NULL, `routine_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `superset_group` INTEGER, `rest_timer_seconds` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
    """CREATE INDEX IF NOT EXISTS `index_routine_exercises_routine_id` ON `routine_exercises` (`routine_id`)""",
    """CREATE INDEX IF NOT EXISTS `index_routine_exercises_exercise_id` ON `routine_exercises` (`exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `routine_sets` (`id` TEXT NOT NULL, `routine_exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `set_type` TEXT NOT NULL, `target_weight_kg` REAL, `target_reps` INTEGER, `target_rep_range_min` INTEGER, `target_rep_range_max` INTEGER, `target_duration_seconds` INTEGER, `target_distance_meters` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`routine_exercise_id`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_routine_sets_routine_exercise_id` ON `routine_sets` (`routine_exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `workouts` (`id` TEXT NOT NULL, `routine_id` TEXT, `title` TEXT NOT NULL, `notes` TEXT, `status` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER, `duration_seconds` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
    """CREATE INDEX IF NOT EXISTS `index_workouts_routine_id` ON `workouts` (`routine_id`)""",
    """CREATE INDEX IF NOT EXISTS `index_workouts_status` ON `workouts` (`status`)""",
    """CREATE INDEX IF NOT EXISTS `index_workouts_started_at` ON `workouts` (`started_at`)""",
    """CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `superset_group` INTEGER, `rest_timer_seconds` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
    """CREATE INDEX IF NOT EXISTS `index_workout_exercises_workout_id` ON `workout_exercises` (`workout_id`)""",
    """CREATE INDEX IF NOT EXISTS `index_workout_exercises_exercise_id` ON `workout_exercises` (`exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `workout_sets` (`id` TEXT NOT NULL, `workout_exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `set_type` TEXT NOT NULL, `weight_kg` REAL, `reps` INTEGER, `duration_seconds` INTEGER, `distance_meters` REAL, `rpe` REAL, `custom_metric` REAL, `is_completed` INTEGER NOT NULL, `completed_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workout_exercise_id`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE INDEX IF NOT EXISTS `index_workout_sets_workout_exercise_id` ON `workout_sets` (`workout_exercise_id`)""",
    """CREATE TABLE IF NOT EXISTS `personal_records` (`id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, `workout_set_id` TEXT, `pr_type` TEXT NOT NULL, `value` REAL NOT NULL, `achieved_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_records_exercise_id_pr_type` ON `personal_records` (`exercise_id`, `pr_type`)""",
    """CREATE INDEX IF NOT EXISTS `index_personal_records_workout_id` ON `personal_records` (`workout_id`)""",
    """CREATE TABLE IF NOT EXISTS `body_measurements` (`date` TEXT NOT NULL, `weight_kg` REAL, `lean_mass_kg` REAL, `fat_percent` REAL, `neck_cm` REAL, `shoulder_cm` REAL, `chest_cm` REAL, `left_bicep_cm` REAL, `right_bicep_cm` REAL, `left_forearm_cm` REAL, `right_forearm_cm` REAL, `abdomen_cm` REAL, `waist_cm` REAL, `hips_cm` REAL, `left_thigh_cm` REAL, `right_thigh_cm` REAL, `left_calf_cm` REAL, `right_calf_cm` REAL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`date`))""",
    """CREATE TABLE IF NOT EXISTS `progress_photos` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `file_path` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
    """CREATE INDEX IF NOT EXISTS `index_progress_photos_date` ON `progress_photos` (`date`)""",
)

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
 *
 * That narrower style (`migrate(db)` called directly, result checked via raw `PRAGMA`/`SELECT`)
 * proves the migration's *SQL* is correct but never exercises Room's own post-migration schema
 * *validation* — the [androidx.room.util.TableInfo] comparison a real `Room.databaseBuilder(...)`
 * open performs, which independently checks every column's default value and the live table's
 * full column set against what the entity declares. `debug13.9` shipped a migration whose SQL was
 * fully correct by every test above, yet crashed on every real upgrade, because of exactly that
 * gap (`ExerciseEntity`'s `muscle_heads` field had no `defaultValue` matching the migration's own
 * `DEFAULT '[]'`, and `primary_muscle_head` had stopped being declared even though the column was
 * deliberately left on the live table). Every migration from `MIGRATION_3_4` onward additionally
 * gets one full end-to-end test (see the bottom of this file) that builds a real `V*_SCHEMA_SQL`
 * database and opens it through [LogEzDatabase]'s own `Room.databaseBuilder(...).addMigrations(...)`
 * path, so Room's real validation runs — not just a hand test's assumptions about what it checks.
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

    /**
     * A "v4" stand-in with just the two tables MIGRATION_4_5 touches (`routines`, `workouts`) --
     * enough columns to insert realistic rows, matching [openV2]/[openV3]'s scope.
     */
    private fun openV4(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `routines` (
                        `id` TEXT NOT NULL, `folder_id` TEXT, `name` TEXT NOT NULL, `notes` TEXT,
                        `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE `workouts` (
                        `id` TEXT NOT NULL, `routine_id` TEXT, `title` TEXT NOT NULL, `notes` TEXT,
                        `status` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER,
                        `duration_seconds` INTEGER NOT NULL, `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`)
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
    fun `MIGRATION_4_5 adds a non-null structure column defaulting to REGULAR on both routines and workouts`() {
        val helper = openV4()
        val db = helper.writableDatabase
        LogEzDatabase.MIGRATION_4_5.migrate(db)

        listOf("routines", "workouts").forEach { table ->
            val cursor = db.query("PRAGMA table_info(`$table`)")
            var found = false
            var type = ""
            var notNull = false
            var default: String? = null
            cursor.use {
                val nameIdx = it.getColumnIndexOrThrow("name")
                val typeIdx = it.getColumnIndexOrThrow("type")
                val notNullIdx = it.getColumnIndexOrThrow("notnull")
                val defaultIdx = it.getColumnIndexOrThrow("dflt_value")
                while (it.moveToNext()) {
                    if (it.getString(nameIdx) == "structure") {
                        found = true
                        type = it.getString(typeIdx)
                        notNull = it.getInt(notNullIdx) == 1
                        default = it.getString(defaultIdx)
                    }
                }
            }
            assertTrue("expected a structure column on $table", found)
            assertEquals("TEXT", type)
            assertTrue("$table.structure must be NOT NULL", notNull)
            assertEquals("'REGULAR'", default)
        }
        db.close()
    }

    @Test
    fun `MIGRATION_4_5 leaves pre-existing routine and workout rows intact, reading back as REGULAR`() {
        val helper = openV4()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO routines (id, folder_id, name, notes, order_index, created_at, updated_at) " +
                "VALUES ('r1', NULL, 'Push Day', NULL, 0, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO workouts (id, routine_id, title, notes, status, started_at, ended_at, duration_seconds, created_at, updated_at) " +
                "VALUES ('w1', NULL, 'Morning Session', NULL, 'COMPLETED', 100, 200, 100, 0, 0)",
        )

        LogEzDatabase.MIGRATION_4_5.migrate(db)

        val routineCursor = db.query("SELECT name, structure FROM routines WHERE id = 'r1'")
        routineCursor.moveToFirst()
        assertEquals("Push Day", routineCursor.getString(0))
        assertEquals("REGULAR", routineCursor.getString(1))
        routineCursor.close()

        val workoutCursor = db.query("SELECT title, structure FROM workouts WHERE id = 'w1'")
        workoutCursor.moveToFirst()
        assertEquals("Morning Session", workoutCursor.getString(0))
        assertEquals("REGULAR", workoutCursor.getString(1))
        workoutCursor.close()
        db.close()
    }

    /**
     * A "v5" stand-in with just the one table MIGRATION_5_6 references (`workout_sets`, the FK
     * target `activity_tracks.workout_set_id` points at) -- matching [openV2]/[openV4]'s same
     * "sufficient for what this migration does" scope.
     */
    private fun openV5(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `workout_sets` (
                        `id` TEXT NOT NULL, `workout_exercise_id` TEXT NOT NULL, `order_index` INTEGER NOT NULL,
                        `set_type` TEXT NOT NULL, `weight_kg` REAL, `reps` INTEGER, `duration_seconds` INTEGER,
                        `distance_meters` REAL, `rpe` REAL, `custom_metric` REAL, `is_completed` INTEGER NOT NULL,
                        `completed_at` INTEGER, PRIMARY KEY(`id`)
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
    fun `MIGRATION_5_6 creates activity_tracks with the entity's exact columns`() {
        val helper = openV5()
        val db = helper.writableDatabase
        LogEzDatabase.MIGRATION_5_6.migrate(db)

        val cursor = db.query("PRAGMA table_info(`activity_tracks`)")
        val columns = mutableMapOf<String, Pair<String, Boolean>>() // name -> (type, notNull)
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            val typeIdx = it.getColumnIndexOrThrow("type")
            val notNullIdx = it.getColumnIndexOrThrow("notnull")
            while (it.moveToNext()) {
                columns[it.getString(nameIdx)] = it.getString(typeIdx) to (it.getInt(notNullIdx) == 1)
            }
        }

        assertEquals(setOf("id", "workout_set_id", "route_polyline", "point_count", "avg_accuracy_m"), columns.keys)
        assertEquals("TEXT" to true, columns["id"])
        assertEquals("TEXT" to true, columns["workout_set_id"])
        assertEquals("TEXT" to false, columns["route_polyline"])
        assertEquals("INTEGER" to true, columns["point_count"])
        assertEquals("REAL" to false, columns["avg_accuracy_m"])

        db.close()
    }

    @Test
    fun `MIGRATION_5_6 can read back a row it just accepted, and is safe to run twice`() {
        val helper = openV5()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO workout_sets (id, workout_exercise_id, order_index, set_type, is_completed) " +
                "VALUES ('set1', 'we1', 0, 'NORMAL', 0)",
        )
        LogEzDatabase.MIGRATION_5_6.migrate(db)
        LogEzDatabase.MIGRATION_5_6.migrate(db) // CREATE TABLE IF NOT EXISTS must not throw on a second run

        db.execSQL(
            "INSERT INTO activity_tracks (id, workout_set_id, route_polyline, point_count, avg_accuracy_m) " +
                "VALUES ('t1', 'set1', 'abc123', 42, 5.5)",
        )
        val cursor = db.query("SELECT workout_set_id, point_count FROM activity_tracks WHERE id = 't1'")
        cursor.moveToFirst()
        assertEquals("set1", cursor.getString(0))
        assertEquals(42, cursor.getInt(1))
        cursor.close()
        db.close()
    }

    /**
     * Same real-open technique as the historical-chain tests below: a database physically built to
     * the real v4 schema (`4.json`'s createSql), opened through [LogEzDatabase]'s own
     * `Room.databaseBuilder(...).addMigrations(...)` path — so Room's post-migration validation
     * checks MIGRATION_4_5's live columns against what [com.enil.logez.core.data.entity.RoutineEntity]
     * and [com.enil.logez.core.data.entity.WorkoutEntity] declare, `defaultValue` included.
     */
    @Test
    fun `a real v4 database opened through LogEzDatabase's own migration path upgrades to v5 without a validation crash`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("repro_v4_to_v5.db")
        dbFile.delete()
        try {
            val seedCallback = object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    V4_SCHEMA_SQL.forEach { db.execSQL(it) }
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val seedConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(seedCallback)
                .build()
            FrameworkSQLiteOpenHelperFactory().create(seedConfig).writableDatabase.close()

            val db = Room.databaseBuilder(context, LogEzDatabase::class.java, dbFile.absolutePath)
                .addMigrations(
                    LogEzDatabase.MIGRATION_1_2, LogEzDatabase.MIGRATION_2_3, LogEzDatabase.MIGRATION_3_4,
                    LogEzDatabase.MIGRATION_4_5, LogEzDatabase.MIGRATION_5_6,
                )
                .build()

            db.openHelper.writableDatabase // forces Room to actually open + migrate + validate
            db.close()
        } finally {
            dbFile.delete()
        }
    }

    /**
     * Reproduces the real upgrade path end to end -- a database physically built to the real v3
     * schema (every `CREATE TABLE`/`CREATE INDEX` statement taken verbatim from the exported
     * `3.json`, not a hand-typed stand-in), then opened through the actual [LogEzDatabase]
     * `Room.databaseBuilder(...).addMigrations(...)` path a real app upgrade takes. This exercises
     * Room's own post-migration schema validation ([androidx.room.util.TableInfo] comparison),
     * which the narrower [openV3]-based tests above never touch -- they call
     * [LogEzDatabase.MIGRATION_3_4]`.migrate(db)` directly and only check the resulting data, not
     * whether Room itself would accept the resulting schema as matching what `ExerciseEntity` v4
     * declares.
     */
    @Test
    fun `a real v3 database opened through LogEzDatabase's own migration path upgrades to the current version without a validation crash`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("repro_v3_to_v4.db")
        dbFile.delete()
        try {
            val seedCallback = object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    V3_SCHEMA_SQL.forEach { db.execSQL(it) }
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val seedConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(seedCallback)
                .build()
            FrameworkSQLiteOpenHelperFactory().create(seedConfig).writableDatabase.close()

            val db = Room.databaseBuilder(context, LogEzDatabase::class.java, dbFile.absolutePath)
                .addMigrations(
                    LogEzDatabase.MIGRATION_1_2, LogEzDatabase.MIGRATION_2_3, LogEzDatabase.MIGRATION_3_4,
                    LogEzDatabase.MIGRATION_4_5, LogEzDatabase.MIGRATION_5_6,
                )
                .build()

            db.openHelper.writableDatabase // forces Room to actually open + migrate + validate
            db.close()
        } finally {
            dbFile.delete()
        }
    }

    /**
     * Same real-open technique as the v3->v4 test above, but starting from the real v1 schema
     * (`1.json`'s `createSql`, an empty install predating `MIGRATION_1_2`) and running the entire
     * historical migration chain in one shot -- the exact path the *oldest* possible real install
     * takes on an upgrade straight to the current version. [MIGRATION_1_2] and [MIGRATION_2_3]
     * only had `migrate(db)`-direct tests before this (per the audit that followed the v3->v4
     * incident): safe by inspection since neither adds a defaulted `NOT NULL` column or drops a
     * declared one, but this closes that coverage gap with the same real-validation technique
     * rather than leaving it asymmetric.
     */
    @Test
    fun `a real v1 database opened through LogEzDatabase's full migration chain upgrades to the current version without a validation crash`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("repro_v1_to_v4.db")
        dbFile.delete()
        try {
            val seedCallback = object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    V1_SCHEMA_SQL.forEach { db.execSQL(it) }
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val seedConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(seedCallback)
                .build()
            FrameworkSQLiteOpenHelperFactory().create(seedConfig).writableDatabase.close()

            val db = Room.databaseBuilder(context, LogEzDatabase::class.java, dbFile.absolutePath)
                .addMigrations(
                    LogEzDatabase.MIGRATION_1_2, LogEzDatabase.MIGRATION_2_3, LogEzDatabase.MIGRATION_3_4,
                    LogEzDatabase.MIGRATION_4_5, LogEzDatabase.MIGRATION_5_6,
                )
                .build()

            db.openHelper.writableDatabase // forces Room to actually open + migrate (1->2->3->4->5) + validate
            db.close()
        } finally {
            dbFile.delete()
        }
    }
}
