package com.enil.logez.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.enil.logez.core.data.LogEzDatabase
import com.enil.logez.core.data.dao.AnalyticsDao
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.dao.GoalDao
import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.dao.RecordsDao
import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.dao.WorkoutDao
import com.enil.logez.core.di.ActiveSessionDataStore
import com.enil.logez.core.di.EntitlementDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LogEzDatabase =
        Room.databaseBuilder(context, LogEzDatabase::class.java, LogEzDatabase.DATABASE_NAME)
            .addMigrations(LogEzDatabase.MIGRATION_1_2, LogEzDatabase.MIGRATION_2_3, LogEzDatabase.MIGRATION_3_4, LogEzDatabase.MIGRATION_4_5)
            .build()

    @Provides
    fun provideExerciseDao(db: LogEzDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideGoalDao(db: LogEzDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideRoutineDao(db: LogEzDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideWorkoutDao(db: LogEzDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideRecordsDao(db: LogEzDatabase): RecordsDao = db.recordsDao()

    @Provides
    fun provideMeasurementDao(db: LogEzDatabase): MeasurementDao = db.measurementDao()

    @Provides
    fun provideAnalyticsDao(db: LogEzDatabase): AnalyticsDao = db.analyticsDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("logez_settings") },
        )

    /** §9.5 — a separate store from settings, deliberately: session state is transient/process-recovery-only. */
    @Provides
    @Singleton
    @ActiveSessionDataStore
    fun provideActiveSessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("logez_active_session") },
        )

    /** ADR-0008 — the offline-grace entitlement cache's own store, deliberately separate from settings. */
    @Provides
    @Singleton
    @EntitlementDataStore
    fun provideEntitlementDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("logez_entitlement") },
        )
}
