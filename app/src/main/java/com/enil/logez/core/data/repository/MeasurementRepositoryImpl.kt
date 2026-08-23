package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.domain.repository.MeasurementRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class MeasurementRepositoryImpl @Inject constructor(
    private val dao: MeasurementDao,
) : MeasurementRepository {
    override fun observeAll(): Flow<List<BodyMeasurementEntity>> = dao.observeAll()
    override suspend fun upsert(measurement: BodyMeasurementEntity) = dao.upsertMeasurement(measurement)
    override suspend fun getLatestWeightKgOnOrBefore(date: String): Double? =
        dao.getLatestWeightOnOrBefore(date)?.weightKg

    override fun observeAllPhotos(): Flow<List<ProgressPhotoEntity>> = dao.observeAllPhotos()
    override suspend fun upsertPhoto(photo: ProgressPhotoEntity) = dao.upsertPhoto(photo)
    override suspend fun getPhotoByDate(date: String): ProgressPhotoEntity? = dao.getPhotoByDate(date)
}
