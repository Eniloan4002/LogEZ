package com.enil.logez.core.domain.repository

import com.enil.logez.core.domain.model.EntitlementSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * ADR-0008: whether the app is unlocked (2-month Play trial, then a ₱50/month subscription).
 * Purchase initiation (`BillingClient.launchBillingFlow`, which needs an `Activity`) is a UI-layer
 * concern for the paywall screen (M19b) — deliberately not on this interface.
 */
interface EntitlementRepository {
    val entitlement: Flow<EntitlementSnapshot>

    /** Forces a fresh Billing Library check, falling back to the offline-grace cache on failure. */
    suspend fun refresh(): EntitlementSnapshot
}
