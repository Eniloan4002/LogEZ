package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    /** One row per date — upsert-by-date (PHASE2_PLAN.md §3.2). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(measurement: BodyMeasurementEntity)

    @Query("SELECT * FROM body_measurements WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): BodyMeasurementEntity?

    /** Most recent entry on or before [date] with a non-null weight — §8.1 bodyweight resolution. */
    @Query(
        "SELECT * FROM body_measurements WHERE date <= :date AND weight_kg IS NOT NULL ORDER BY date DESC LIMIT 1",
    )
    suspend fun getLatestWeightOnOrBefore(date: String): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("DELETE FROM body_measurements WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: ProgressPhotoEntity)

    @Query("SELECT * FROM progress_photos WHERE date = :date LIMIT 1")
    suspend fun getPhotoByDate(date: String): ProgressPhotoEntity?

    @Query("SELECT * FROM progress_photos ORDER BY date DESC")
    fun observeAllPhotos(): Flow<List<ProgressPhotoEntity>>

    @Delete
    suspend fun deletePhoto(photo: ProgressPhotoEntity)
}
