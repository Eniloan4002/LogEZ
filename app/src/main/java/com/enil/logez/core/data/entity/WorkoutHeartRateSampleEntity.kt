package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M21f. One row per Health-Connect-sourced heart-rate sample recorded during a workout's own
 * `startedAt`..`endedAt` window -- read once at Finish and cached locally (same "local cache of
 * what Health Connect already tracks" shape as [DailyWellnessTotalEntity]), so the post-workout
 * summary's historical chart never needs to re-query Health Connect. Workout-scoped, not
 * set-scoped (a wearable's heart rate isn't tied to any one exercise), unlike [ActivityTrackEntity].
 */
@Entity(
    tableName = "workout_heart_rate_samples",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_id")],
)
data class WorkoutHeartRateSampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "bpm") val bpm: Long,
)
