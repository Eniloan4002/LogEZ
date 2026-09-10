package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * M21e. One row per calendar day, upserted whenever the app reads a fresh Health Connect
 * aggregate for that date -- a local cache of what Health Connect already tracks, not a second
 * source of truth for step/calorie counting. [date] is the ISO-8601 (`yyyy-MM-dd`) string primary
 * key, same per-date convention as [BodyMeasurementEntity].
 */
@Entity(tableName = "daily_wellness_totals")
data class DailyWellnessTotalEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "steps") val steps: Long,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Double?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
