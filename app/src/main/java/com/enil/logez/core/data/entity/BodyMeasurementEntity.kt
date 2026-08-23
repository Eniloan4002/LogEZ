package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One per-date entry, all 17 metrics unlocked (PHASE2_PLAN.md §3.2, §3 scope decisions — no
 * former Pro gate here). [date] is the ISO-8601 (`yyyy-MM-dd`) string primary key; one row per
 * calendar day, upserted by [com.enil.logez.core.data.dao.MeasurementDao].
 */
@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "lean_mass_kg") val leanMassKg: Double?,
    @ColumnInfo(name = "fat_percent") val fatPercent: Double?,
    @ColumnInfo(name = "neck_cm") val neckCm: Double?,
    @ColumnInfo(name = "shoulder_cm") val shoulderCm: Double?,
    @ColumnInfo(name = "chest_cm") val chestCm: Double?,
    @ColumnInfo(name = "left_bicep_cm") val leftBicepCm: Double?,
    @ColumnInfo(name = "right_bicep_cm") val rightBicepCm: Double?,
    @ColumnInfo(name = "left_forearm_cm") val leftForearmCm: Double?,
    @ColumnInfo(name = "right_forearm_cm") val rightForearmCm: Double?,
    @ColumnInfo(name = "abdomen_cm") val abdomenCm: Double?,
    @ColumnInfo(name = "waist_cm") val waistCm: Double?,
    @ColumnInfo(name = "hips_cm") val hipsCm: Double?,
    @ColumnInfo(name = "left_thigh_cm") val leftThighCm: Double?,
    @ColumnInfo(name = "right_thigh_cm") val rightThighCm: Double?,
    @ColumnInfo(name = "left_calf_cm") val leftCalfCm: Double?,
    @ColumnInfo(name = "right_calf_cm") val rightCalfCm: Double?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
