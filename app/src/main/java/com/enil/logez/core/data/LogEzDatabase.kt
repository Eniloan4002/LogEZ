package com.enil.logez.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enil.logez.core.data.dao.AnalyticsDao
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.dao.GoalDao
import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.dao.RecordsDao
import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.dao.WorkoutDao
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity

/**
 * Schema v1 (PHASE2_PLAN.md §3.2, §10.4), v2 adds `goal_definitions` (M8d), v3 adds
 * `exercises.primary_muscle_head` (M8e), v4 replaces that with `exercises.muscle_heads` (M8e
 * revision — a checklist, not a single pick). `exportSchema = true` from day one — `schemas/` is
 * committed alongside this file. `fallbackToDestructiveMigration` is never used anywhere in this
 * app (project-rules.md testing expectations): this app's entire value is the historical log, so
 * every schema change ships as a real, tested [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        ExerciseEntity::class,
        RoutineFolderEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        RoutineSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        BodyMeasurementEntity::class,
        ProgressPhotoEntity::class,
        GoalDefinitionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LogEzDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun recordsDao(): RecordsDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val DATABASE_NAME = "logez.db"

        /** v1 -> v2 (M8d): a brand-new table only, nothing existing changes shape. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_definitions` (
                        `id` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `period` TEXT NOT NULL,
                        `target_value` REAL NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v2 -> v3 (M8e): one new nullable column, no existing data touched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `primary_muscle_head` TEXT")
            }
        }

        /**
         * v3 -> v4 (M8e revision — Owner feedback: heads should be a checklist, not a single
         * pick). Adds `muscle_heads` (a JSON list, matching `secondary_muscle_groups`'s existing
         * convention) and carries forward any already-set `primary_muscle_head` as that head's
         * single-element list. `primary_muscle_head` itself is left in place, unused, rather than
         * dropped — `DROP COLUMN` support varies across the SQLite versions bundled with API
         * 26-36 (this app's minSdk-to-target range). Unlike a hand-written schema, this does
         * *not* mean the column can simply stop being declared in [ExerciseEntity]: Room's
         * post-migration validation ([androidx.room.util.TableInfo]) compares the live table's
         * *entire* column set against the entity's, so an undeclared-but-still-present column
         * fails validation exactly like a genuinely missing one would — see
         * [ExerciseEntity.deprecatedPrimaryMuscleHead] (this exact gap crashed every upgrade in
         * `debug13.9`, caught and fixed the same day via [LogEzDatabaseMigrationTest]'s real-open
         * reproduction test).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `muscle_heads` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    """
                    UPDATE `exercises`
                    SET `muscle_heads` = '["' || `primary_muscle_head` || '"]'
                    WHERE `primary_muscle_head` IS NOT NULL
                    """.trimIndent(),
                )
            }
        }
    }
}
