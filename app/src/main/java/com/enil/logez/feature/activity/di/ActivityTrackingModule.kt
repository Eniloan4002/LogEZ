package com.enil.logez.feature.activity.di

import android.content.Context
import com.enil.logez.feature.activity.location.FusedLocationSource
import com.enil.logez.feature.activity.location.LocationSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** M21a — a vendor-SDK, builder-constructed client, the same shape as `BillingModule`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityTrackingModule {
    @Binds
    @Singleton
    abstract fun bindLocationSource(impl: FusedLocationSource): LocationSource

    companion object {
        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
    }
}
