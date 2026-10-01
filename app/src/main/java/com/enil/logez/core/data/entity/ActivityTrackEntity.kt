package com.enil.logez.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M21a. One row per GPS-tracked `WorkoutSetEntity` — the route/quality data a manually-typed set
 * never needed. `routePolyline` is populated once at tracking finish (an encoded fix sequence,
 * [com.enil.logez.core.common.PolylineEncoding]), not per-fix, to avoid a write-heavy table for a
 * long run. Distance/duration themselves stay on the existing `workout_sets` columns — this table
 * is supplementary, per the domain-model decision to extend the existing Workout tables rather
 * than build a parallel domain (decisions.md 2026-09-09).
 */
@Entity(
    tableName = "activity_tracks",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_set_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_set_id", unique = true)],
)
data class ActivityTrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_set_id") val workoutSetId: String,
    @ColumnInfo(name = "route_polyline") val routePolyline: String?,
    @ColumnInfo(name = "point_count") val pointCount: Int,
    @ColumnInfo(name = "avg_accuracy_m") val avgAccuracyM: Double?,
    /**
     * Seconds since tracking started at which each route point was recorded, one per point, in
     * [com.enil.logez.core.common.PolylineEncoding.encodeDeltas] form. Added in v10 (2026-09-26)
     * so the summary can rebuild splits and a pace chart; null for every run recorded before it,
     * whose summary therefore shows no splits.
     */
    @ColumnInfo(name = "route_times") val routeTimes: String? = null,
    /**
     * Seconds-since-start ranges during which the run was paused, as start/end pairs in the same
     * delta encoding as [routeTimes] (see [com.enil.logez.core.common.PolylineEncoding.encodeDeltas]).
     * Added in v11 (2026-10-01) for the tracking screen's Pause. Null means the run was never
     * paused, which is what every run recorded before it was, so those read exactly as before. Route
     * times stay on the clock (pauses included) so heart-rate samples keep matching by time; this
     * column is what lets splits and moving time leave the paused stretches out.
     */
    @ColumnInfo(name = "pause_ranges") val pauseRanges: String? = null,
)
