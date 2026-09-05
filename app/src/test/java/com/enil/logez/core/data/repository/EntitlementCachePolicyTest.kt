package com.enil.logez.core.data.repository

import com.enil.logez.core.domain.model.EntitlementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0008: the local offline-grace decision (`resolveOfflineSnapshot`) is the only genuinely
 * branching logic in `EntitlementRepositoryImpl` — kept pure specifically so it's testable here
 * without BillingClient/DataStore/Robolectric. Owner-confirmed grace window: 3 days.
 */
class EntitlementCachePolicyTest {
    private val gracePeriodMillis = 3 * 24 * 60 * 60 * 1000L
    private val now = 1_767_603_600_000L // matches FakeClock's fixed anchor

    @Test
    fun `no cache resolves to UNKNOWN`() {
        val snapshot = resolveOfflineSnapshot(cached = null, nowMillis = now, gracePeriodMillis = gracePeriodMillis)
        assertEquals(EntitlementStatus.UNKNOWN, snapshot.status)
        assertTrue(snapshot.isFromCache)
        assertEquals(null, snapshot.lastVerifiedAtMillis)
    }

    @Test
    fun `cache within the grace window replays ENTITLED as-is`() {
        val cached = CachedEntitlement(EntitlementStatus.ENTITLED, lastVerifiedAtMillis = now - gracePeriodMillis / 2)
        val snapshot = resolveOfflineSnapshot(cached, now, gracePeriodMillis)
        assertEquals(EntitlementStatus.ENTITLED, snapshot.status)
        assertTrue(snapshot.isFromCache)
        assertEquals(cached.lastVerifiedAtMillis, snapshot.lastVerifiedAtMillis)
    }

    @Test
    fun `cache within the grace window replays PENDING as-is`() {
        val cached = CachedEntitlement(EntitlementStatus.PENDING, lastVerifiedAtMillis = now - 1_000L)
        val snapshot = resolveOfflineSnapshot(cached, now, gracePeriodMillis)
        assertEquals(EntitlementStatus.PENDING, snapshot.status)
    }

    @Test
    fun `cache within the grace window does not resurrect a NOT_ENTITLED device`() {
        val cached = CachedEntitlement(EntitlementStatus.NOT_ENTITLED, lastVerifiedAtMillis = now - 1_000L)
        val snapshot = resolveOfflineSnapshot(cached, now, gracePeriodMillis)
        assertEquals(EntitlementStatus.NOT_ENTITLED, snapshot.status)
    }

    @Test
    fun `exactly at the grace boundary still counts as within the window`() {
        val cached = CachedEntitlement(EntitlementStatus.ENTITLED, lastVerifiedAtMillis = now - gracePeriodMillis)
        val snapshot = resolveOfflineSnapshot(cached, now, gracePeriodMillis)
        assertEquals(EntitlementStatus.ENTITLED, snapshot.status)
    }

    @Test
    fun `just past the grace window fails closed to NOT_ENTITLED but keeps the stale timestamp`() {
        val cached = CachedEntitlement(EntitlementStatus.ENTITLED, lastVerifiedAtMillis = now - gracePeriodMillis - 1L)
        val snapshot = resolveOfflineSnapshot(cached, now, gracePeriodMillis)
        assertEquals(EntitlementStatus.NOT_ENTITLED, snapshot.status)
        assertTrue(snapshot.isFromCache)
        assertEquals(cached.lastVerifiedAtMillis, snapshot.lastVerifiedAtMillis)
    }
}
