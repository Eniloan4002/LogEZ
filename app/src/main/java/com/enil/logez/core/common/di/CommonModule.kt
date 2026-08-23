package com.enil.logez.core.common.di

import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
