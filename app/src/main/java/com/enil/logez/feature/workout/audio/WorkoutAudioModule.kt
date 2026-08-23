package com.enil.logez.feature.workout.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutAudioModule {
    @Binds
    @Singleton
    abstract fun bindWorkoutAudioPlayer(impl: SoundPoolWorkoutAudioPlayer): WorkoutAudioPlayer

    @Binds
    @Singleton
    abstract fun bindWorkoutHapticsPlayer(impl: SystemWorkoutHapticsPlayer): WorkoutHapticsPlayer
}
