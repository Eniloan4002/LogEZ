package com.enil.logez.fakes

import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake (PHASE2_PLAN.md §10.1 rule 2). Bodyweight is the only field M4c's volume/PR math
 * reads. [bodyweightKg] gives one weight for all of history; [weightsByDate] models a weight that
 * actually changed over time, which is what §8.3's per-workout resolution depends on — the lookup
 * honours "on or before", so a workout dated between two measurements sees the earlier one.
 */
class FakeMeasurementRepository(
    private val bodyweightKg: Double? = null,
    private val weightsByDate: Map<String, Double> = emptyMap(),
) : MeasurementRepository {
    private val state = MutableStateFlow<List<BodyMeasurementEntity>>(emptyList())
    private val photos = MutableStateFlow<List<ProgressPhotoEntity>>(emptyList())

    /** Every date this fake was asked about, in call order — proves the resolution is per-workout. */
    val queriedDates = mutableListOf<String>()

    override fun observeAll(): Flow<List<BodyMeasurementEntity>> = state
    override suspend fun upsert(measurement: BodyMeasurementEntity) {
        state.value = state.value.filterNot { it.date == measurement.date } + measurement
    }

    override suspend fun getLatestWeightKgOnOrBefore(date: String): Double? {
        queriedDates += date
        if (weightsByDate.isEmpty()) return bodyweightKg
        return weightsByDate.entries.filter { it.key <= date }.maxByOrNull { it.key }?.value
    }

    override fun observeAllPhotos(): Flow<List<ProgressPhotoEntity>> = photos
    override suspend fun upsertPhoto(photo: ProgressPhotoEntity) {
        photos.value = photos.value.filterNot { it.date == photo.date } + photo
    }

    override suspend fun getPhotoByDate(date: String): ProgressPhotoEntity? = photos.value.find { it.date == date }
}
