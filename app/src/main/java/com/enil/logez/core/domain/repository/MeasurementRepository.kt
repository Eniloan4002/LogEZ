package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository {
    fun observeAll(): Flow<List<BodyMeasurementEntity>>
    suspend fun upsert(measurement: BodyMeasurementEntity)

    /** §8.1 bodyweight resolution: newest entry on/before [date] with a non-null weight. */
    suspend fun getLatestWeightKgOnOrBefore(date: String): Double?

    fun observeAllPhotos(): Flow<List<ProgressPhotoEntity>>
    suspend fun upsertPhoto(photo: ProgressPhotoEntity)
    suspend fun getPhotoByDate(date: String): ProgressPhotoEntity?
}
