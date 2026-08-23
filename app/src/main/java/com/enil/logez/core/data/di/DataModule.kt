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
import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.dao.RecordsDao
import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.dao.WorkoutDao
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
        Room.databaseBuilder(context, LogEzDatabase::class.java, LogEzDatabase.DATABASE_NAME).build()

    @Provides
    fun provideExerciseDao(db: LogEzDatabase): ExerciseDao = db.exerciseDao()

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
}
