package com.enil.logez.core.data.repository

import com.android.billingclient.api.Purchase
import com.enil.logez.core.domain.model.EntitlementStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `mapToStatus` takes a plain [PurchaseInfo], not the real Play `Purchase` (which parses JSON in
 * its constructor and throws outside Robolectric) — see the KDoc on `mapToStatus` itself.
 */
class EntitlementStatusMapperTest {
    @Test
    fun `no matching purchase maps to NOT_ENTITLED`() {
        assertEquals(EntitlementStatus.NOT_ENTITLED, mapToStatus(null))
    }

    @Test
    fun `a pending purchase maps to PENDING`() {
        val purchase = PurchaseInfo(purchaseState = Purchase.PurchaseState.PENDING, isAcknowledged = false)
        assertEquals(EntitlementStatus.PENDING, mapToStatus(purchase))
    }

    @Test
    fun `a purchased purchase maps to ENTITLED regardless of acknowledgement state`() {
        val acknowledged = PurchaseInfo(purchaseState = Purchase.PurchaseState.PURCHASED, isAcknowledged = true)
        val unacknowledged = PurchaseInfo(purchaseState = Purchase.PurchaseState.PURCHASED, isAcknowledged = false)
        assertEquals(EntitlementStatus.ENTITLED, mapToStatus(acknowledged))
        assertEquals(EntitlementStatus.ENTITLED, mapToStatus(unacknowledged))
    }

    @Test
    fun `an unspecified purchase state maps to NOT_ENTITLED`() {
        val purchase = PurchaseInfo(purchaseState = Purchase.PurchaseState.UNSPECIFIED_STATE, isAcknowledged = false)
        assertEquals(EntitlementStatus.NOT_ENTITLED, mapToStatus(purchase))
    }
}
