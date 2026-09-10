package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.WellnessDao
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.domain.repository.WellnessRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WellnessRepositoryImpl @Inject constructor(
    private val dao: WellnessDao,
) : WellnessRepository {
    override suspend fun upsert(total: DailyWellnessTotalEntity) = dao.upsert(total)
    override suspend fun getByDate(date: String): DailyWellnessTotalEntity? = dao.getByDate(date)
    override fun observeByDate(date: String): Flow<DailyWellnessTotalEntity?> = dao.observeByDate(date)
}
