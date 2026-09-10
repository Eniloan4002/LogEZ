package com.enil.logez.feature.wellness.di

import com.enil.logez.feature.wellness.HealthConnectMetricsSource
import com.enil.logez.feature.wellness.HealthMetricsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** M21e — same shape as `ActivityTrackingModule`'s `LocationSource` binding. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WellnessModule {
    @Binds
    @Singleton
    abstract fun bindHealthMetricsSource(impl: HealthConnectMetricsSource): HealthMetricsSource
}
