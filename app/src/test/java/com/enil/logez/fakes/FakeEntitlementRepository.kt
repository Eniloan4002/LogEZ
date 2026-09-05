package com.enil.logez.fakes

import com.enil.logez.core.domain.model.EntitlementSnapshot
import com.enil.logez.core.domain.model.EntitlementStatus
import com.enil.logez.core.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeEntitlementRepository(
    initial: EntitlementSnapshot = EntitlementSnapshot(EntitlementStatus.UNKNOWN, null, isFromCache = false),
) : EntitlementRepository {
    val state = MutableStateFlow(initial)
    override val entitlement: Flow<EntitlementSnapshot> = state

    var refreshCallCount = 0
        private set

    override suspend fun refresh(): EntitlementSnapshot {
        refreshCallCount++
        return state.value
    }
}
