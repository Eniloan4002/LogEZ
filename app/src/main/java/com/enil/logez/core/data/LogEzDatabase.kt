package com.enil.logez.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.enil.logez.core.data.dao.AnalyticsDao
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.dao.RecordsDao
import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.dao.WorkoutDao
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ExerciseEntity
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
 * Schema v1 (PHASE2_PLAN.md §3.2, §10.4). `exportSchema = true` from day one — `schemas/` is
 * committed alongside this file. `fallbackToDestructiveMigration` is never used anywhere in
 * this app (project-rules.md testing expectations): this app's entire value is the historical
 * log, so every future schema change ships as a real, tested [androidx.room.migration.Migration].
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
    ],
    version = 1,
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

    companion object {
        const val DATABASE_NAME = "logez.db"
    }
}
