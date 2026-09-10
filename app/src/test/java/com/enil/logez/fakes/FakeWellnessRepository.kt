package com.enil.logez.fakes

import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.domain.repository.WellnessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeWellnessRepository(initial: List<DailyWellnessTotalEntity> = emptyList()) : WellnessRepository {
    private val state = MutableStateFlow(initial.associateBy { it.date })

    val all: List<DailyWellnessTotalEntity> get() = state.value.values.toList()

    override suspend fun upsert(total: DailyWellnessTotalEntity) {
        state.value = state.value + (total.date to total)
    }

    override suspend fun getByDate(date: String): DailyWellnessTotalEntity? = state.value[date]

    override fun observeByDate(date: String): Flow<DailyWellnessTotalEntity?> = state.map { it[date] }
}
